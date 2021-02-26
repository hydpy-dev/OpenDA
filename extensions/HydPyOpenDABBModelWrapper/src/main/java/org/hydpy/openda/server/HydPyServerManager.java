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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the life-cycle of the {@link HydPyServer}, allowing possibly several server processes at once.
 * Model runs will the same instanceId are guaranteed to always get the same server process.
 *
 * @author Gernot Belger
 */
public final class HydPyServerManager
{
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

  public static synchronized void create( final HydPyServerConfiguration hydPyConfig )
  {
    if( FIXED_ITEMS == null )
      throw new IllegalStateException( "initFixedParameters was never called" );

    if( INSTANCE != null )
      throw new IllegalStateException( "create wa called more than once" );

    final HydPyServerBuilder serverBuilder = new HydPyServerBuilder( hydPyConfig );

    INSTANCE = new HydPyServerManager( serverBuilder, hydPyConfig.maxProcesses, FIXED_ITEMS );
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

    return new HydPyServerInstance( instanceId, server );
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
      final HydPyServerProcess process = m_serverBuilder.start( processId );

      /* wrap for OpenDA specific calling */
      final HydPyServerOpenDA server = new HydPyServerOpenDA( process, m_fixedParameters );

      /* return the real implementation which is always threaded per process */
      return new HydPyServerThreadingProcess( server );
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