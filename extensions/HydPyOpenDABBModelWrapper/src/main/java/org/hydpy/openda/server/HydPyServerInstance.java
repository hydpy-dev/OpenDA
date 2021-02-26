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
class HydPyServerInstance implements IHydPyInstance
{
  private final String m_instanceId;

  private final IHydPyServerProcess m_server;

  HydPyServerInstance( final String instanceId, final IHydPyServerProcess server ) throws HydPyServerException
  {
    m_instanceId = instanceId;
    m_server = server;

    m_server.initializeInstance( instanceId );
  }

  @Override
  public List<IServerItem> getItems( )
  {
    return m_server.getItems();
  }

  @Override
  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    return m_server.getItemValues( m_instanceId );
  }

  @Override
  public void setItemValues( final String instanceId, final Collection<IPrevExchangeItem> values ) throws HydPyServerException
  {
    m_server.setItemValues( m_instanceId, values );
  }

  @Override
  public void simulate( final String instanceId ) throws HydPyServerException
  {
    m_server.simulate( instanceId );
  }
}