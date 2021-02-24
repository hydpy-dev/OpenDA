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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.openda.interfaces.IPrevExchangeItem;

/**
 * @author Gernot Belger
 */
class HydPyServerThreadingProcess implements IHydPyServerProcess
{
  private final IHydPyServerProcess m_delegate;

  private final ExecutorService m_executor;

  public HydPyServerThreadingProcess( final IHydPyServerProcess delegate )
  {
    m_delegate = delegate;

    final ThreadFactory threadFactory = new ThreadFactory()
    {
      @Override
      public Thread newThread( final Runnable r )
      {
        final ThreadGroup group = Thread.currentThread().getThreadGroup();
        final Thread thread = new Thread( group, r, getName() );
        // FIXME
        thread.setDaemon( true );
        thread.setPriority( Thread.NORM_PRIORITY );
        return thread;
      }
    };
    m_executor = Executors.newSingleThreadExecutor( threadFactory );
  }

  protected final IHydPyServerProcess getDelegate( )
  {
    return m_delegate;
  }

  public String getName( )
  {
    // FIXME
    return toString();
  }

  private HydPyServerException toHydPyServerException( final Exception e )
  {
    final Throwable cause = e.getCause();
    if( cause instanceof HydPyServerException )
      return (HydPyServerException)cause;

    return new HydPyServerException( cause );
  }

  @Override
  public List<IServerItem> getItems( )
  {
    /* not threaded, this is called only once during initialization */
    return getDelegate().getItems();
  }

  @Override
  public IHydPyInstance createInstance( final String instanceId ) throws HydPyServerException
  {
    return getDelegate().createInstance( instanceId );
  }

  @Override
  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    final Callable<List<IPrevExchangeItem>> task = new Callable<List<IPrevExchangeItem>>()
    {
      @Override
      public List<IPrevExchangeItem> call( ) throws HydPyServerException
      {
        return getDelegate().getItemValues( instanceId );
      }
    };
    final Future<List<IPrevExchangeItem>> future = m_executor.submit( task );

    try
    {
      return future.get();
    }
    catch( final InterruptedException | ExecutionException e )
    {
      throw toHydPyServerException( e );
    }
  }

  @Override
  public void setItemValues( final String instanceId, final Collection<IPrevExchangeItem> values )
  {
    final Callable<Void> task = new Callable<Void>()
    {
      @Override
      public Void call( ) throws HydPyServerException
      {
        getDelegate().setItemValues( instanceId, values );
        return null;
      }
    };

    m_executor.submit( task );
  }

  @Override
  public void simulate( final String instanceId )
  {
    final Callable<Void> task = new Callable<Void>()
    {
      @Override
      public Void call( ) throws HydPyServerException
      {
        getDelegate().simulate( instanceId );
        return null;
      }
    };

    m_executor.submit( task );
  }

  @Override
  public void shutdown( )
  {
    final Callable<Void> task = new Callable<Void>()
    {
      @Override
      public Void call( )
      {
        getDelegate().shutdown();
        return null;
      }
    };

    try
    {
      m_executor.submit( task );

      m_executor.shutdown();
      m_executor.awaitTermination( 5, TimeUnit.SECONDS );
      System.out.format( "%s: shut down terminated", getName() );
    }
    catch( final InterruptedException e )
    {
      e.printStackTrace();
    }
  }

  @Override
  public void kill( )
  {
    /* not threaded, especially executor was already shut down */
    getDelegate().kill();
  }
}