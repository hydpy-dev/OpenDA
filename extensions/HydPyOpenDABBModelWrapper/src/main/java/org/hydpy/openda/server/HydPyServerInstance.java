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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.openda.interfaces.IExchangeItem;

/**
 * Represents one server instance which can be used by it's associated model instances to run simulations on.
 *
 * @author Gernot Belger
 */
final class HydPyServerInstance
{
  private final Map<String, Future<List<IExchangeItem>>> m_currentSimulations = new HashMap<>();

  private final Map<String, List<File>> m_fileToDeleteAfterGetItems = new HashMap<>();

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

  public Collection<HydPyExchangeItemDescription> getItems( )
  {
    /* not threaded, the items are always available after initialization */
    return getServer().getItems();
  }

  public synchronized void initializeInstance( final String instanceId, final HydPyInstanceDirs instanceDirs )
  {
    final Callable<List<IExchangeItem>> callable = ( ) -> getServer().initializeInstance( instanceId, instanceDirs );

    final Future<List<IExchangeItem>> future = HydPyUtils.submitAndLogExceptions( m_executor, callable );

    m_currentSimulations.put( instanceId, future );
  }

  public List<IExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    final Future<List<IExchangeItem>> currentSimulation = m_currentSimulations.get( instanceId );
    if( currentSimulation == null )
      throw new HydPyServerException( "Get item values before simulation/initialization" );

    try
    {
      final List<IExchangeItem> itemValues = currentSimulation.get();
      // we keep the last simulation forever in case of consecutive 'getItemsValues'
      // m_currentSimulation = null;

      /* we can now also delete files that had been moarked for deletion */
      deleteFilesMarkedForDeletion( instanceId );

      return itemValues;
    }
    catch( final InterruptedException | ExecutionException e )
    {
      throw toHydPyServerException( e );
    }
  }

  private void deleteFilesMarkedForDeletion( final String instanceId )
  {
    final List<File> filesToDelete = m_fileToDeleteAfterGetItems.computeIfAbsent( instanceId, key -> new ArrayList<>() );
    filesToDelete.forEach( file -> {
      try
      {
        FileUtils.forceDelete( file );
      }
      catch( final IOException e )
      {
        e.printStackTrace();
      }
    } );
    filesToDelete.clear();
  }

  public void setItemValues( final String instanceId, final Collection<IExchangeItem> values )
  {
    final Callable<Void> callable = ( ) -> {
      getServer().setItemValues( instanceId, values );
      return null;
    };

    HydPyUtils.submitAndLogExceptions( m_executor, callable );
  }

  public synchronized String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    return getServer().getItemNames( itemId );
  }

  public synchronized void restoreInternalState( final String instanceId, final File stateConditionsDir, final boolean deleteFiles )
  {
    // REMARK: we always restore the conditions fetch the current exchange item state in one call
    final Future<List<IExchangeItem>> future = HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().restoreInternalState( instanceId, stateConditionsDir ) );
    m_currentSimulations.put( instanceId, future );

    if( deleteFiles )
    {
      final List<File> filesToDelete = m_fileToDeleteAfterGetItems.computeIfAbsent( instanceId, key -> new ArrayList<>() );
      filesToDelete.add( stateConditionsDir );
    }
  }

  public synchronized void simulate( final String instanceId, final File outputControlDir )
  {
    // REMARK: we always directly simulate and fetch the results in one call
    final Future<List<IExchangeItem>> future = HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().simulate( instanceId, outputControlDir ) );
    m_currentSimulations.put( instanceId, future );
  }

  public synchronized void shutdown( )
  {
    HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().shutdown() );
  }

  public synchronized void writeConditions( final String instanceId, final File outputConditionsDir ) throws HydPyServerException
  {
    final Callable<Void> callable = ( ) -> {
      getServer().writeConditions( instanceId, outputConditionsDir );
      return null;
    };

    try
    {
      final Future<Void> future = HydPyUtils.submitAndLogExceptions( m_executor, callable );
      // REMARK: block until everything is written
      future.get();
    }
    catch( InterruptedException | ExecutionException e )
    {
      throw toHydPyServerException( e );
    }
  }
}