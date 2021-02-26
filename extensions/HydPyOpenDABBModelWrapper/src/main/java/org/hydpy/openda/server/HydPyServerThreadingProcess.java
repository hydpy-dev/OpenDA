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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.openda.interfaces.IPrevExchangeItem;

/**
 * @author Gernot Belger
 */
class HydPyServerThreadingProcess implements IHydPyServerProcess
{
  private final HydPyServerOpenDA m_server;

  private final ExecutorService m_executor;

  private final Map<String, Future<List<IPrevExchangeItem>>> m_currentSimulations = new HashMap<>();

  public HydPyServerThreadingProcess( final HydPyServerOpenDA server, final ExecutorService executor )
  {
    m_server = server;
    m_executor = executor;
  }

  protected final HydPyServerOpenDA getServer( )
  {
    return m_server;
  }

  @Override
  public String getName( )
  {
    return getServer().getName();
  }

  private HydPyServerException toHydPyServerException( final Exception e )
  {
    final Throwable cause = e.getCause();
    if( cause instanceof HydPyServerException )
      return (HydPyServerException)cause;

    return new HydPyServerException( cause );
  }

  @Override
  public synchronized void initializeInstance( final String instanceId )
  {
    final Callable<List<IPrevExchangeItem>> task = new Callable<List<IPrevExchangeItem>>()
    {
      @Override
      public List<IPrevExchangeItem> call( ) throws HydPyServerException
      {
        return getServer().initializeInstance( instanceId );
      }
    };

    m_currentSimulations.put( instanceId, m_executor.submit( task ) );
  }

  @Override
  public List<IServerItem> getItems( )
  {
    /* not threaded, the items are always available after initialization */
    return getServer().getItems();
  }

  @Override
  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    final Future<List<IPrevExchangeItem>> currentSimulation = m_currentSimulations.get( instanceId );
    if( currentSimulation != null )
    {
      try
      {
        final List<IPrevExchangeItem> itemValues = currentSimulation.get();
        // we keep the last simulation forever in case of consecutive 'getItemsValues'
        // m_currentSimulation = null;
        return itemValues;
      }
      catch( final InterruptedException | ExecutionException e )
      {
        throw toHydPyServerException( e );
      }
    }

    throw new HydPyServerException( "Get item values before simulation/initialization" );
  }

  @Override
  public void setItemValues( final String instanceId, final Collection<IPrevExchangeItem> values )
  {
    final Callable<Void> task = new Callable<Void>()
    {
      @Override
      public Void call( ) throws HydPyServerException
      {
        getServer().setItemValues( instanceId, values );
        return null;
      }
    };

    m_executor.submit( task );
  }

  @Override
  public synchronized void simulate( final String instanceId )
  {
    final Callable<List<IPrevExchangeItem>> task = new Callable<List<IPrevExchangeItem>>()
    {
      @Override
      public List<IPrevExchangeItem> call( ) throws HydPyServerException
      {
        // TODO comment
        return getServer().simulate( instanceId );
      }
    };

    m_currentSimulations.put( instanceId, m_executor.submit( task ) );
  }

  @Override
  public void shutdown( )
  {
    final Callable<Void> task = new Callable<Void>()
    {
      @Override
      public Void call( )
      {
        getServer().shutdown();
        return null;
      }
    };

    m_executor.submit( task );
  }

  @Override
  public void kill( )
  {
    /* not threaded, especially executor was already shut down */
    getServer().kill();
  }
}