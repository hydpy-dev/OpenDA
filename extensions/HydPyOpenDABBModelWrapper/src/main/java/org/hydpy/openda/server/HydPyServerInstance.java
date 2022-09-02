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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.openda.interfaces.IExchangeItem;

/**
 * Represents one server instance which can be used by it's associated model instances to run simulations on.
 *
 * @author Gernot Belger
 */
final class HydPyServerInstance
{
  private final List<Future< ? >> m_pendingTasks = new LinkedList<>();

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

  public synchronized void checkPendingTasks( ) throws HydPyServerException
  {
    // REMARK: checks the 'pending' tasks (i.e. tasks where normally get is never called)
    // if they are finished and force an exception in the main thread if they failed.

    final List<Future< ? >> x = m_pendingTasks;
    for( final Iterator<Future< ? >> iterator = x.iterator(); iterator.hasNext(); )
    {
      final Future< ? > future = iterator.next();
      if( future.isDone() )
      {
        iterator.remove();

        try
        {
          future.get();
        }
        catch( final InterruptedException | ExecutionException e )
        {
          throw HydPyUtils.toHydPyServerException( e );
        }
      }
    }
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

  public synchronized List<IExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    checkPendingTasks();

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
      throw HydPyUtils.toHydPyServerException( e );
    }
  }

  private void deleteFilesMarkedForDeletion( final String instanceId )
  {
    final List<File> filesToDelete = m_fileToDeleteAfterGetItems.computeIfAbsent( instanceId, key -> new ArrayList<>() );

    // REMARK: delegate to a separate thread, in order to minimalize blocking the main thread
    FileDeletionThread.instance().addFilesForDeletion( filesToDelete );

    filesToDelete.clear();
  }

  public synchronized void setItemValues( final String instanceId, final Collection<IExchangeItem> values ) throws HydPyServerException
  {
    checkPendingTasks();

    final Callable<Void> callable = ( ) -> {
      getServer().setItemValues( instanceId, values );
      return null;
    };

    final Future<Void> future = HydPyUtils.submitAndLogExceptions( m_executor, callable );
    // REMARK: specially remember task, where get normally is never called.
    // We will check for exceptions of these special tasks, else OpenDA will keep running even if exceptions have occured.
    m_pendingTasks.add( future );
  }

  public synchronized String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    return getServer().getItemNames( itemId );
  }

  public synchronized void restoreInternalState( final String instanceId, final File stateConditionsDir, final boolean deleteFiles ) throws HydPyServerException
  {
    checkPendingTasks();

    // REMARK: we always restore the conditions fetch the current exchange item state in one call
    final Future<List<IExchangeItem>> future = HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().restoreInternalState( instanceId, stateConditionsDir ) );
    m_currentSimulations.put( instanceId, future );

    if( deleteFiles )
    {
      final List<File> filesToDelete = m_fileToDeleteAfterGetItems.computeIfAbsent( instanceId, key -> new ArrayList<>() );
      filesToDelete.add( stateConditionsDir );
    }
  }

  public synchronized void simulate( final String instanceId, final File outputControlDir ) throws HydPyServerException
  {
    checkPendingTasks();

    // REMARK: we always directly simulate and fetch the results in one call
    final Future<List<IExchangeItem>> future = HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().simulate( instanceId, outputControlDir ) );
    m_currentSimulations.put( instanceId, future );
  }

  public synchronized void closeServer( ) throws HydPyServerException
  {
    // REMARK: we do NOT check for pending tasks here, else shutdown will not terminate correctly

    final Future<Void> future = HydPyUtils.submitAndLogExceptions( m_executor, ( ) -> getServer().closeServer() );

    // REMARK: specially remember task, where get normally is never called.
    // We will check for exceptions ofthese special tasks, else OpenDA will keep running even if exceptions have occured.
    m_pendingTasks.add( future );
  }

  public synchronized Future<Void> writeConditions( final String instanceId, final File outputConditionsDir ) throws HydPyServerException
  {
    checkPendingTasks();

    final Callable<Void> callable = ( ) -> {
      getServer().writeConditions( instanceId, outputConditionsDir );
      return null;
    };

    return HydPyUtils.submitAndLogExceptions( m_executor, callable );
  }
}