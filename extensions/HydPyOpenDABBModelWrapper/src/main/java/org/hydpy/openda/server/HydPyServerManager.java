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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
   * @see #getOrCreateInstance(String)
   */
  public static final String ANY_INSTANCE = "ANY_INSTANCE"; //$NON-NLS-1$

  private static HydPyServerManager INSTANCE = null;

  public static void create( final Path workingDir, final Properties args )
  {
    if( INSTANCE != null )
      throw new IllegalStateException( "create wa called more than once" );

    final HydPyServerConfiguration hydPyConfig = new HydPyServerConfiguration( workingDir, args );

    INSTANCE = new HydPyServerManager( hydPyConfig );
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

  private final Map<Integer, HydPyServerStarter> m_starters = new HashMap<>();

  private final Map<String, HydPyModelInstance> m_instances = new HashMap<>();

  private final HydPyServerConfiguration m_config;

  private int m_nextProcessId = 0;

  public HydPyServerManager( final HydPyServerConfiguration config )
  {
    m_config = config;

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
      m_starters.put( processId, new HydPyServerStarter( m_config, processId ) );

    return m_starters.get( processId );
  }

  /**
   * @param instanceId
   *          Used to fetch a server instance for the given simulation instance id. <br/>
   *          If {@link #ANY_INSTANCE} is given, any available instance is returned (useful for initialization).<br/>
   *          Several calls with the same instanceId are guaranteed to return the same {@link HydPyModelInstance} process.<br/>
   *          Depending on the configuration of this manager, multiple instanceId's may be mapped to the same HydPy server process.
   */
  public synchronized HydPyModelInstance getOrCreateInstance( final String instanceId )
  {
    if( !m_instances.containsKey( instanceId ) )
      m_instances.put( instanceId, createInstance( instanceId ) );

    return m_instances.get( instanceId );
  }

  private HydPyModelInstance createInstance( final String instanceId )
  {
    final int processId = toServerId( instanceId );

    final HydPyServerInstance server = getOrCreateServer( processId );

    return new HydPyModelInstance( instanceId, server );
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
    try
    {
      /* start the real server (if that is not yet the case) and wait for it */
      final HydPyServerStarter starter = getOrCreateStarter( processId );
      return starter.getServer();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to start HydPy Server", e );
    }
  }

  public synchronized void killAllServers( )
  {
    for( final HydPyServerStarter starter : m_starters.values() )
      starter.kill();
  }

  public synchronized void finish( )
  {
    for( final HydPyServerStarter starter : m_starters.values() )
      starter.shutdown();
  }
}