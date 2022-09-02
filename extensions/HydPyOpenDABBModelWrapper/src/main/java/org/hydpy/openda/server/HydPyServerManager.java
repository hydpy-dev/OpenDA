/**
 * Copyright (c) 2019 by
 * - Bundesanstalt für Gewässerkunde
 * - Björnsen Beratende Ingenieure GmbH
 * All rights reserved.
 *
 * This file is Free Software under the under the terms of the
 * GNU Lesser General Public License (LGPL >=v3)
 * and comes with ABSOLUTELY NO WARRANTY! Check out the
 * documentation coming with HydPy for details.
 */
package org.hydpy.openda.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.hydpy.openda.HydPyInstanceConfiguration;

/**
 * Manages the life-cycle of the {@link HydPyServerInstance}s, allowing possibly several server processes at once.
 * Model runs will the same instanceId are guaranteed to always get the same server process.
 *
 * @author Gernot Belger
 */
public final class HydPyServerManager
{
  /**
   * Constant for any server instance.
   *
   * @see #getOrCreateInstance(String, File)
   */
  public static final String ANY_INSTANCE = "ANY_INSTANCE"; //$NON-NLS-1$

  private static HydPyServerManager INSTANCE = null;

  public static void create( final File workingDir, final String filename )
  {
    final Path configFile = workingDir.toPath().resolve( filename ).normalize();

    if( INSTANCE != null )
    {
      /*
       * If both create are with the exact same config file, we ignore the second call
       * Else, we protect the user from possible confusion by throwing a hard error.
       */
      if( INSTANCE.m_configFile.compareTo( configFile ) == 0 )
        return;

      final String message = String.format( "HydPy Server initialization failed.%nThe server configuration is applied twice with different configuration files.%nPlease check your observer and stoch-model configurations.%n%s%n%s%n", configFile, INSTANCE.m_configFile );
      throw new IllegalStateException( message );
    }

    final String versionAndTimestamp = findVersionAndTimestamp();
    System.out.println( versionAndTimestamp );

    HydPyRequirements.checkOpenDaVersion( System.out );

    final Properties args = readConfiguration( configFile );

    final HydPyServerConfiguration hydPyConfig = new HydPyServerConfiguration( workingDir.toPath(), args );

    final HydPyInstanceConfiguration instanceDirs = HydPyInstanceConfiguration.read( workingDir, args );

    INSTANCE = new HydPyServerManager( hydPyConfig, instanceDirs, configFile );
  }

  private static String findVersionAndTimestamp( )
  {
    final String className = HydPyServerManager.class.getSimpleName() + ".class";
    final String classPath = HydPyServerManager.class.getResource( className ).toString();
    if( !classPath.startsWith( "jar" ) )
      return "HydPyOpenDABBModelWrapper (Development)";

    final StringBuilder buffer = new StringBuilder();

    final String manifestPath = classPath.substring( 0, classPath.lastIndexOf( "!" ) + 1 ) + "/" + JarFile.MANIFEST_NAME;
    try( final InputStream is = new URL( manifestPath ).openStream() )
    {
      final Manifest manifest = new Manifest( is );
      final Attributes attr = manifest.getMainAttributes();
      if( attr != null )
      {
        final String name = attr.getValue( "Bundle-Name" );
        if( name != null )
        {
          buffer.append( name );
          buffer.append( " - " );
        }

        final String version = attr.getValue( "Bundle-Version" );
        if( version != null )
        {
          buffer.append( version );
          buffer.append( " - " );
        }

        final String timestamp = attr.getValue( "Build-Timestamp" );
        if( timestamp != null )
          buffer.append( timestamp );
      }
    }
    catch( final Exception e )
    {
      buffer.append( "Error opening manifest " + manifestPath );
      e.printStackTrace();
    }

    return buffer.toString();
  }

  private static Properties readConfiguration( final Path configFile )
  {
    final Properties args = new Properties();

    try
    {
      // REMARK: ugly but necessary for cases where we do not have influence how
      // paths are created in the .properties file (aka FEWS)
      // Replace all backslashes with slashes (hopefully we never have real escape sequences here)
      final String content = FileUtils.readFileToString( configFile.toFile(), StandardCharsets.ISO_8859_1 );
      final String contentNoBackslashes = content.replace( '\\', '/' );

      final StringReader reader = new StringReader( contentNoBackslashes );
      args.load( reader );
      return args;
    }
    catch( final IOException e )
    {
      e.printStackTrace();
      final String message = String.format( "Failed to read HydPy server configuration file: %s", configFile );
      throw new RuntimeException( message, e );
    }
  }

  public synchronized static HydPyServerManager instance( )
  {
    if( INSTANCE == null )
      throw new IllegalStateException( "create was never called" );

    return INSTANCE;
  }

  private static final class ShutdownThread extends Thread
  {
    private final HydPyServerManager m_manager;

    public ShutdownThread( final HydPyServerManager manager )
    {
      super( "HydPyServer shutdown" );

      m_manager = manager;
    }

    @Override
    public void run( )
    {
      try
      {
        /* try to finish gracefully */
        m_manager.finish();
      }
      catch( final Exception e )
      {
        /* protect against any exceptions so python processes stil may be killed */
        e.printStackTrace();
      }

      /* but still kill all processes if that fails */
      m_manager.killAllServers();
    }
  }

  private final Map<Integer, HydPyServerStarter> m_starters = new HashMap<>();

  private final Map<String, HydPyModelInstance> m_instances = new HashMap<>();

  private final HydPyServerConfiguration m_config;

  private final HydPyInstanceConfiguration m_instanceDirs;

  private int m_nextProcessId = 0;

  private final Path m_configFile;

  public HydPyServerManager( final HydPyServerConfiguration config, final HydPyInstanceConfiguration instanceDirs, final Path configFile )
  {
    m_config = config;
    m_instanceDirs = instanceDirs;
    m_configFile = configFile;

    // REMARK: always try to shutdown the running HydPy servers.
    Runtime.getRuntime().addShutdownHook( new ShutdownThread( this ) );

    if( config.parallelStartup )
    {
      for( int i = 0; i < config.maxProcesses; i++ )
        getOrCreateStarter( i );
    }
  }

  private HydPyServerStarter getOrCreateStarter( final int processId )
  {
    if( !m_starters.containsKey( processId ) )
      m_starters.put( processId, new HydPyServerStarter( m_config, m_instanceDirs, processId ) );

    return m_starters.get( processId );
  }

  /**
   * @param instanceId
   *          Used to fetch a server instance for the given simulation instance id. <br/>
   *          If {@link #ANY_INSTANCE} is given, any available instance is returned (useful for initialization).<br/>
   *          Several calls with the same instanceId are guaranteed to return the same {@link HydPyModelInstance} process.<br/>
   *          Depending on the configuration of this manager, multiple instanceId's may be mapped to the same HydPy server process.
   */
  public synchronized HydPyModelInstance getOrCreateInstance( final String instanceId, final File instanceDir )
  {
    if( !m_instances.containsKey( instanceId ) )
    {
      final HydPyModelInstance instance = createInstance( instanceId, instanceDir );
      m_instances.put( instanceId, instance );
    }

    return m_instances.get( instanceId );
  }

  private HydPyModelInstance createInstance( final String instanceId, final File instanceDir )
  {
    final int processId = toServerId( instanceId );

    final HydPyServerInstance server = getOrCreateServer( processId );

    final File hydpyModelDir = m_config.modelDir.toFile();
    final HydPyInstanceDirs instanceDirs = m_instanceDirs.resolve( instanceId, instanceDir, hydpyModelDir );

    return new HydPyModelInstance( instanceId, instanceDirs, server );
  }

  private int toServerId( final String instanceId )
  {
    if( instanceId == ANY_INSTANCE )
      return 0;

    /* simply distributing the instances to consecutive processes, restarting with first process if we encounter maxProcesses */
    final int currentProcessId = m_nextProcessId;

    m_nextProcessId = (m_nextProcessId + 1) % m_config.maxProcesses;

    return currentProcessId;
  }

  private HydPyServerInstance getOrCreateServer( final int processId )
  {
    /* start the real server (if that is not yet the case) and wait for it */
    final HydPyServerStarter starter = getOrCreateStarter( processId );
    return starter.getServer();
  }

  synchronized void killAllServers( )
  {
    for( final HydPyServerStarter starter : m_starters.values() )
      starter.kill();
  }

  synchronized void finish( )
  {
    /* let each instance write its conditions */
    System.out.println( "Start HydPy finalization (may take a while if writing conditions is configured)" );
    final List<Future<Void>> conditionsFutures = m_instances.values().stream() //
        .map( instance -> {
          try
          {
            return instance.writeFinalConditions();
          }
          catch( final Exception e )
          {
            e.printStackTrace();
            return null;
          }
        } )//
        .filter( Objects::nonNull ) //
        .collect( Collectors.toList() );

    /* wait until all conditions are written */
    waitForGetAll( conditionsFutures );

    /* start shutdown for all servers (asynchronous for each server) */
    m_starters.values().forEach( HydPyServerStarter::closeServerAndWaitForProcessEnd );

    /* shutdown executors and await executor termination (synchronous, waits for termination of all tasks) */
    m_starters.values().forEach( HydPyServerStarter::terminate );

    /* also check a last time for pending futures */
    m_starters.values().forEach( starter -> {
      try
      {
        starter.getServer().checkPendingTasks();
      }
      catch( final Exception e )
      {
        e.printStackTrace();
      }
    } );
  }

  private void waitForGetAll( final List<Future<Void>> futures )
  {
    for( final Future<Void> future : futures )
    {
      try
      {
        future.get();
      }
      catch( final Exception e )
      {
        e.printStackTrace();
      }
    }
  }
}