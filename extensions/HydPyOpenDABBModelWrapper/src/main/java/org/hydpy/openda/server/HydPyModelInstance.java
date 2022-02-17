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

  private HydPyExchangeCache m_itemCache;

  private final HydPyInstanceDirs m_instanceDirs;

  HydPyModelInstance( final String instanceId, final HydPyInstanceDirs instanceDirs, final HydPyServerInstance server )
  {
    m_instanceId = instanceId;
    m_instanceDirs = instanceDirs;
    m_server = server;

    m_server.initializeInstance( instanceId, instanceDirs );

    // FIXME: move some code...
    // TODO actively call series reader and input conditions here?
  }

  public Collection<IServerItem> getItems( )
  {
    return m_server.getItems();
  }

  public synchronized List<IExchangeItem> getItemValues( ) throws HydPyServerException
  {
    final List<IExchangeItem> itemValues = m_server.getItemValues( m_instanceId );

    // FIXME: apply partial hydpy state to complete cache state here

    if( m_itemCache == null )
      m_itemCache = new HydPyExchangeCache( itemValues );

    return m_itemCache.getItemValues( itemValues );
  }

  public synchronized void setItemValues( final Collection<IExchangeItem> values )
  {
    /**
     * Update the cache with the current time-slice of the given values, and then set the
     * values covering only the time-slice to hydpy
     */
    final Collection<IExchangeItem> itemValues = m_itemCache.updateItemValues( values );
    m_server.setItemValues( m_instanceId, itemValues );
  }

  public void simulate( )
  {
    m_server.simulate( m_instanceId );
  }

  public String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    return m_server.getItemNames( itemId );
  }

  public void writeConditions( )
  {
    final File outputConditionsDir = m_instanceDirs.getOutputConditionsDir();
    if( outputConditionsDir != null )
      m_server.writeConditions( m_instanceId, outputConditionsDir );
  }
}