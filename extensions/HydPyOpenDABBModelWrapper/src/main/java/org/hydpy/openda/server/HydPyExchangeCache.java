/**
 * Copyright (c) 2021 by
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

import org.openda.interfaces.IExchangeItem;

/**
 * Bridges the gap between HydPy and OpenDA concerning the contents of an exchange item:
 * - OpenDA assumes the exchange items to cover the whole model time
 * - HydPy Server only exchanges data within the current simulation time
 * The cache hence holds the complete exchange items in memory (and uses these for OpenDA)
 *
 * @author Gernot Belger
 */
class HydPyExchangeCache
{
  private final List<IExchangeItem> m_initialValues;

  public HydPyExchangeCache( final List<IExchangeItem> initialValues )
  {
    // ITEM_ID_FIRST_DATE
    m_initialValues = initialValues;
  }

  public List<IExchangeItem> getItemValues( final List<IExchangeItem> itemValues )
  {
    // TODO Auto-generated method stub
    return itemValues;
  }

  public Collection<IExchangeItem> updateItemValues( final Collection<IExchangeItem> values )
  {
    // TODO Auto-generated method stub
    return values;
  }
}