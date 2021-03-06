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
 * Represents one server instance which can be used by it's associated model instances to run simulations on.
 *
 * @author Gernot Belger
 */
final class HydPyServerInstance
{
  private final Map<String, Future<List<IPrevExchangeItem>>> m_currentSimulations = new HashMap<>();

  private final HydPyOpenDACaller m_server;

  private final ExecutorService m_executor;

  public HydPyServerInstance( final HydPyOpenDACaller server, final ExecutorService executor )
  {
    m_server = server;
    m_executor = executor;
  }

  protected HydPyOpenDACaller getServer( )
  {
    return m_server;
  }

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

  public Collection<IServerItem> getItems( )
  {
    /* not threaded, the items are always available after initialization */
    @SuppressWarnings( "unchecked" ) final Collection<IServerItem> items = (Collection<IServerItem>)getServer().getItems();
    return items;
  }

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

  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    final Future<List<IPrevExchangeItem>> currentSimulation = m_currentSimulations.get( instanceId );
    if( currentSimulation == null )
      throw new HydPyServerException( "Get item values before simulation/initialization" );

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

  public synchronized void simulate( final String instanceId )
  {
    final Callable<List<IPrevExchangeItem>> task = new Callable<List<IPrevExchangeItem>>()
    {
      @Override
      public List<IPrevExchangeItem> call( ) throws HydPyServerException
      {
        // REMARK: we always directly simulate and fetch the results in one call
        return getServer().simulate( instanceId );
      }
    };

    m_currentSimulations.put( instanceId, m_executor.submit( task ) );
  }

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
}