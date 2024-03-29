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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openda.interfaces.IExchangeItem;

/**
 * Wraps a 'real' HydPyServer together with an instanceId.
 *
 * @author Gernot Belger
 */
public final class HydPyModelInstance
{
  public static final String ITEM_ID_FIRST_DATE = "firstdate_sim"; //$NON-NLS-1$

  public static final String ITEM_ID_LAST_DATE = "lastdate_sim"; //$NON-NLS-1$

  public static final String ITEM_ID_STEP_SIZE = "stepsize"; //$NON-NLS-1$

  private final String m_instanceId;

  private final HydPyServerInstance m_server;

  private final HydPyInstanceDirs m_instanceDirs;

  HydPyModelInstance( final String instanceId, final HydPyInstanceDirs instanceDirs, final HydPyServerInstance server )
  {
    m_instanceId = instanceId;
    m_instanceDirs = instanceDirs;
    m_server = server;

    m_server.initializeInstance( instanceId, instanceDirs );
  }

  public Collection<HydPyExchangeItemDescription> getItems( )
  {
    return m_server.getItems();
  }

  public synchronized List<IExchangeItem> getItemValues( ) throws HydPyServerException
  {
    return m_server.getItemValues( m_instanceId );
  }

  public synchronized void setItemValues( final Collection<IExchangeItem> values ) throws HydPyServerException
  {
    m_server.setItemValues( m_instanceId, values );
  }

  /**
   * @param deleteFiles
   *          If <code>true</code>, the files will be deleted after the state is restored. This must happen inside the implementation, because we need to wait until hydpy really has read them.
   */
  public void restoreInternalState( final File stateConditionsDir, final boolean deleteFiles ) throws HydPyServerException
  {
    m_server.restoreInternalState( m_instanceId, stateConditionsDir, deleteFiles );
  }

  public void simulate( ) throws HydPyServerException
  {
    final File outputControlDir = m_instanceDirs.getOutputControlDir();
    m_server.simulate( m_instanceId, outputControlDir );
  }

  public String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    return m_server.getItemNames( itemId );
  }

  public void writeConditions( final File outputConditionsFileOrDir ) throws HydPyServerException
  {
    try
    {
      final Future<Void> future = m_server.writeConditions( m_instanceId, outputConditionsFileOrDir );
      // REMARK: block until everything is written in this case
      future.get();
    }
    catch( final InterruptedException | ExecutionException e )
    {
      throw HydPyUtils.toHydPyServerException( e );
    }
  }

  public Future<Void> writeFinalConditions( ) throws HydPyServerException
  {
    final File outputConditionsDir = m_instanceDirs.getOutputConditionsDir();
    if( outputConditionsDir == null )
      return null;

    return m_server.writeConditions( m_instanceId, outputConditionsDir );
  }
}