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

import org.openda.interfaces.IPrevExchangeItem;

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

  HydPyModelInstance( final String instanceId, final HydPyServerInstance server )
  {
    m_instanceId = instanceId;
    m_server = server;

    m_server.initializeInstance( instanceId );
  }

  public Collection<IServerItem> getItems( )
  {
    return m_server.getItems();
  }

  public List<IPrevExchangeItem> getItemValues( ) throws HydPyServerException
  {
    return m_server.getItemValues( m_instanceId );
  }

  public void setItemValues( final Collection<IPrevExchangeItem> values )
  {
    m_server.setItemValues( m_instanceId, values );
  }

  public void simulate( )
  {
    m_server.simulate( m_instanceId );
  }

  public String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    return m_server.getItemNames( itemId );
  }
}