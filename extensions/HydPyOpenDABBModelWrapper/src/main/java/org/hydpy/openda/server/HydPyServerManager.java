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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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

  private static Collection<String> FIXED_ITEMS = null;

  public static void initFixedItems( final Collection<String> fixedItems )
  {
    FIXED_ITEMS = fixedItems;
  }

  public static synchronized void create( final Path baseDir, final Properties args )
  {
    if( FIXED_ITEMS == null )
      throw new IllegalStateException( "initFixedParameters was never called" );

    if( INSTANCE != null )
      throw new IllegalStateException( "create wa called more than once" );

    /* absolute paths from system environment */
    final String serverExe = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_PYTHON_EXE, HYD_PY_PYTHON_EXE_DEFAULT );
    final String hydPyScript = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_SCRIPT_PATH, HYD_PY_SCRIPT_PATH_DEFAULT );

    /* Everything else from model.xml arguments */
    final int startPort = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_SERVER_PORT );

    final int maxProcesses = HydPyUtils.getOptionalPropertyAsInt( args, PROPERTY_SERVER_MAX_PROCESSES, 1 );
    if( maxProcesses < 1 )
      throw new RuntimeException( String.format( "Argument '%s': must be positive", PROPERTY_SERVER_MAX_PROCESSES ) );

    if( startPort + maxProcesses - 1 > 0xFFFF )
      throw new RuntimeException( String.format( "Arguments '%s'+'%s': exceeds maximal possible port 0xFFFF", PROPERTY_SERVER_PORT, PROPERTY_SERVER_MAX_PROCESSES ) );

    final int initRetrySeconds = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_INITIALIZE_SECONDS );

    final String projectDirArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_PATH );
    final Path projectDir = baseDir.resolve( projectDirArgument ).normalize();
    if( !Files.isDirectory( projectDir ) )
      throw new RuntimeException( String.format( "Argument '%s': Directory does not exist: %s", PROPERTY_PROJECT_PATH, projectDir ) );

    final String projectName = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_NAME );

    final String configFileArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_CONFIG_FILE );
    final Path configFile = baseDir.resolve( configFileArgument ).normalize();
    if( !Files.isRegularFile( configFile ) )
      throw new RuntimeException( String.format( "Argument '%s': File does not exist: %s", PROPERTY_CONFIG_FILE, configFile ) );

    final String logDirectoryArgument = args.getProperty( PROPERTY_LOG_DIRECTORY, null );
    final Path logDirectory = logDirectoryArgument == null ? null : baseDir.resolve( logDirectoryArgument ).normalize();

    final HydPyServerBuilder serverBuilder = new HydPyServerBuilder( startPort, serverExe, hydPyScript, projectDir, projectName, logDirectory, configFile, initRetrySeconds );

    INSTANCE = new HydPyServerManager( serverBuilder, maxProcesses, FIXED_ITEMS );
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

  private final int m_maxProcesses;

  private final Map<String, IHydPyInstance> m_instances = new HashMap<>();

  private final Map<Integer, IHydPyServerProcess> m_processes = new HashMap<>();

  private final Collection<String> m_fixedParameters;

  private int m_nextProcessId = 0;

  private final HydPyServerBuilder m_serverBuilder;

  public HydPyServerManager( final HydPyServerBuilder serverBuilder, final int maxProcesses, final Collection<String> fixedItemIds )
  {
    m_fixedParameters = fixedItemIds;
    m_maxProcesses = maxProcesses;
    m_serverBuilder = serverBuilder;

    // REMARK: always try to shutdown the running HydPy servers.
    Runtime.getRuntime().addShutdownHook( new ShutdownThread( this ) );
  }

  /**
   * @param instanceId
   *          Used to fetch a server instance for the given simulation instance id. <br/>
   *          If {@link #ANY_INSTANCE} is given, any available instance is returned (useful for initialization).<br/>
   *          Several calls with the same instanceId are guaranteed to return the same {@link IHydPyServer} process.<br/>
   *          Depending on the configuration of this manager, multiple instanceId's may be mapped to the same HydPy server process.
   */
  public synchronized IHydPyInstance getOrCreateInstance( final String instanceId ) throws HydPyServerException
  {
    if( !m_instances.containsKey( instanceId ) )
      m_instances.put( instanceId, createInstance( instanceId ) );

    return m_instances.get( instanceId );
  }

  private IHydPyInstance createInstance( final String instanceId ) throws HydPyServerException
  {
    final int processId = toServerId( instanceId );

    final IHydPyServerProcess server = getOrCreateServer( processId );

    return server.createInstance( instanceId );
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
      /* start the real server */
      final HydPyServerProcess server = m_serverBuilder.start( processId );

//      return new HydPyServerThreadingProcess( server );

      return new HydPyServerOpenDA( server, m_fixedParameters );
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to start HydPy Server", e );
    }
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