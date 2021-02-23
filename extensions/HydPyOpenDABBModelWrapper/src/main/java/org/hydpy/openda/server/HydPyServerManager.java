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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.http.client.utils.URIBuilder;
import org.hydpy.openda.HydPyUtils;

/**
 * Manages the life-cycle of the {@link HydPyServer}, allowing possibly several server processes at once.
 * Model runs will the same instanceId are guaranteed to always get the same server process.
 *
 * @author Gernot Belger
 */
public final class HydPyServerManager
{
  private static final String ENVIRONMENT_HYD_PY_PYTHON_EXE = "HYD_PY_PYTHON_EXE"; //$NON-NLS-1$

  private static final String ENVIRONMENT_HYD_PY_SCRIPT_PATH = "HYD_PY_SCRIPT_PATH"; //$NON-NLS-1$

  private static final String PROPERTY_SERVER_PORT = "serverPort"; //$NON-NLS-1$

  private static final String PROPERTY_SERVER_MAX_PROCESSES = "serverInstances"; //$NON-NLS-1$

  private static final String PROPERTY_INITIALIZE_SECONDS = "initializeWaitSeconds"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_PATH = "projectPath"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_NAME = "projectName"; //$NON-NLS-1$

  private static final String PROPERTY_CONFIG_FILE = "configFile"; //$NON-NLS-1$

  private static final String PROPERTY_LOG_DIRECTORY = "logDirectory"; //$NON-NLS-1$

  private static final String HYD_PY_PYTHON_EXE_DEFAULT = "python.exe"; //$NON-NLS-1$

  private static final String HYD_PY_SCRIPT_PATH_DEFAULT = "hy.py"; //$NON-NLS-1$

  /**
   * Constant for any server instance.
   *
   * @see #getOrCreateServer(String)
   */
  public static final String ANY_INSTANCE = "ANY_INSTANCE"; //$NON-NLS-1$

  private static HydPyServerManager INSTANCE = null;

  private static Collection<String> FIXED_PARAMETERS = null;

  public static void initFixedParameters( final Collection<String> fixedParameters )
  {
    FIXED_PARAMETERS = fixedParameters;
  }

  public static synchronized void create( final Path baseDir, final Properties args )
  {
    if( FIXED_PARAMETERS == null )
      throw new IllegalStateException( "initFixedParameters was never called" );

    INSTANCE = new HydPyServerManager( baseDir, args, FIXED_PARAMETERS );
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
      m_manager.killAllServers();
    }
  }

  private final String m_host;

  private final int m_startPort;

  private final int m_maxProcesses;

  private final String m_serverExe;

  private final int m_initRetrySeconds;

  private final String m_hydPyScript;

  private final String m_projectDir;

  private final String m_projectName;

  private final Map<String, IHydPyServer> m_instances = new HashMap<>();

  private final Map<Integer, IHydPyServerProcess> m_processes = new HashMap<>();

  private final String m_configFile;

  private final Collection<String> m_fixedParameters;

  private int m_nextProcessId = 0;

  private final Path m_logDirectory;

  private HydPyServerManager( final Path baseDir, final Properties args, final Collection<String> fixedParameters )
  {
    m_fixedParameters = fixedParameters;

    // REMARK: always try to shutdown the running HydPy servers.
    Runtime.getRuntime().addShutdownHook( new ShutdownThread( this ) );

    // REAMRK: we open a local process, so this is always localhost (for now)
    m_host = "localhost";

    /* absolute paths from system environment */
    m_serverExe = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_PYTHON_EXE, HYD_PY_PYTHON_EXE_DEFAULT );
    m_hydPyScript = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_SCRIPT_PATH, HYD_PY_SCRIPT_PATH_DEFAULT );

    /* Everything else from model.xml arguments */
    m_startPort = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_SERVER_PORT );

    m_maxProcesses = HydPyUtils.getOptionalPropertyAsInt( args, PROPERTY_SERVER_MAX_PROCESSES, 1 );
    if( m_maxProcesses < 1 )
      throw new RuntimeException( String.format( "Argument '%s': must be positive", PROPERTY_SERVER_MAX_PROCESSES ) );

    if( m_startPort + m_maxProcesses - 1 > 0xFFFF )
      throw new RuntimeException( String.format( "Arguments '%s'+'%s': exceeds maximal possible port 0xFFFF", PROPERTY_SERVER_PORT, PROPERTY_SERVER_MAX_PROCESSES ) );

    m_initRetrySeconds = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_INITIALIZE_SECONDS );

    final String projectDirArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_PATH );
    final Path projectDir = baseDir.resolve( projectDirArgument ).normalize();
    if( !Files.isDirectory( projectDir ) )
      throw new RuntimeException( String.format( "Argument '%s': Directory does not exist: %s", PROPERTY_PROJECT_PATH, projectDir ) );
    m_projectDir = projectDir.toString();

    m_projectName = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_NAME );

    final String configFileArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_CONFIG_FILE );
    final Path configFile = baseDir.resolve( configFileArgument ).normalize();
    if( !Files.isRegularFile( configFile ) )
      throw new RuntimeException( String.format( "Argument '%s': File does not exist: %s", PROPERTY_CONFIG_FILE, configFile ) );
    m_configFile = configFile.toString();

    final String logDirectory = args.getProperty( PROPERTY_LOG_DIRECTORY, null );
    m_logDirectory = logDirectory == null ? null : baseDir.resolve( logDirectory ).normalize();
  }

  /**
   * @param instanceId
   *          Used to fetch a server instance for the given simulation instance id. <br/>
   *          If {@link #ANY_INSTANCE} is given, any available instance is returned (useful for initialization).<br/>
   *          Several calls with the same instanceId are guaranteed to return the same {@link IHydPyServer} process.<br/>
   *          Depending on the configuration of this manager, multiple instanceId's may be mapped to the same HydPy server process.
   */
  public synchronized IHydPyServer getOrCreateServer( final String instanceId ) throws HydPyServerException
  {
    if( !m_instances.containsKey( instanceId ) )
      m_instances.put( instanceId, createInstance( instanceId ) );

    return m_instances.get( instanceId );
  }

  private IHydPyServer createInstance( final String instanceId ) throws HydPyServerException
  {
    final int processId = toServerId( instanceId );

    final IHydPyServerProcess server = getOrCreateServer( processId );

    return HydPyServerInstance.create( instanceId, server );
  }

  private int toServerId( final String instanceId )
  {
    if( instanceId == ANY_INSTANCE )
      return 0;

    /* simply distributing the instances to consecutive processes, restarting with first process if we encounter maxProcesses */
    final int currentProcessId = m_nextProcessId;

    m_nextProcessId = (m_nextProcessId + 1) % m_maxProcesses;

    return currentProcessId;
  }

  private IHydPyServerProcess getOrCreateServer( final int processId )
  {
    if( !m_processes.containsKey( processId ) )
      m_processes.put( processId, createServer( processId ) );

    return m_processes.get( processId );
  }

  private IHydPyServerProcess createServer( final int processId )
  {
    try
    {
      final int port = m_startPort + processId;

      final URI address = new URIBuilder() //
          .setScheme( "http" ) //
          .setHost( m_host ) //
          .setPort( port ) //
          .build();

      final String name = String.format( "HydPyServer %d - %s", processId, port );

      /* start the real server */
      final Process process = startHydPyProcess( port, processId );

      final HydPyServerProcess server = new HydPyServerProcess( name, address, process, m_fixedParameters );

      tryCallServer( server );

//      return new HydPyServerThreadingProcess( server );
      return server;
    }
    catch( final URISyntaxException | IOException | HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to start HydPy Server", e );
    }
  }

  private void tryCallServer( final HydPyServerProcess server ) throws HydPyServerException
  {
    final int retries = m_initRetrySeconds * 4;
    final int timeoutMillis = 250;

    for( int i = 0; i < retries; i++ )
    {
      try
      {
        if( server.checkStatus( timeoutMillis ) )
          return;
      }
      catch( final HydPyServerException ignored )
      {
        // TODO: use logging framework?
        System.out.println( "Waiting for HyPy-Server: " + i );
        /* continue waiting */
      }
      catch( final HydPyServerProcessException e )
      {
        /* the process was terminated, we will never get an answer now */
        e.printStackTrace();
        throw new HydPyServerException( e.getMessage() );
      }
    }

    server.killServer();

    final String message = String.format( "Timeout waiting for HydPy-Server after %d seconds", m_initRetrySeconds );
    throw new HydPyServerException( message );
  }

  private Process startHydPyProcess( final int port, final int processId ) throws IOException
  {
    final File projectDir = new File( m_projectDir );
    final File workingDir = projectDir;

    final String command = m_serverExe;
    final String operation = "start_server";
    final String portArgument = Integer.toString( port );

    final ProcessBuilder builder = new ProcessBuilder( command, m_hydPyScript, operation, portArgument, m_projectName, m_configFile ) //
        .directory( workingDir );

    if( m_logDirectory == null )
      builder.inheritIO();
    else
    {
      final File logFile = new File( m_logDirectory.toFile(), String.format( "HydPy_Server_%d.log", processId ) );
      final File errFile = new File( m_logDirectory.toFile(), String.format( "HydPy_Server_%d.err", processId ) );
      builder.redirectError( errFile );
      builder.redirectOutput( logFile );
    }

    return builder.start();
  }

  public void killAllServers( )
  {
    for( final IHydPyServerProcess process : m_processes.values() )
      process.kill();
  }

  public void finish( )
  {
    for( final IHydPyServerProcess process : m_processes.values() )
      process.shutdown();
  }
}