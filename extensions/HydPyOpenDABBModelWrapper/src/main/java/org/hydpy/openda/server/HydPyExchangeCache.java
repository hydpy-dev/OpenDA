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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.Instant;
import org.openda.interfaces.IExchangeItem;

/**
 * Bridges the gap between HydPy and OpenDA concerning the contents of an exchange item:
 * - OpenDA algorithm assume (more or less) the exchange items to cover the whole model time
 * - HydPy Server only exchanges data within the current simulation time span
 * The cache hence holds the complete exchange items in memory (and uses these for OpenDA)
 *
 * @author Gernot Belger
 */
class HydPyExchangeCache
{
  /*
   * Current (full) state of the model values
   * They will be initialized by the first call to HydPy while the simulation range is stil the original range.
   * After each change by HydPy or OpenDA, they will be updated for the current simulation range.
   */
  private final Map<String, Object> m_modelRangeValues;

  public HydPyExchangeCache( final Map<String, Object> firstValues )
  {
    m_modelRangeValues = new HashMap<>( firstValues );
  }

  public List<IExchangeItem> parseItemValue( final AbstractServerItem<Object> item, final Object currentRangeValue )
  {
    final String id = item.getId();

    /**
     * currentRange value is the value within the current range, as received from HydPy
     * To OpenDa we communicate a value that covers the full (aka model) range.
     */
    final Object oldModelRangeValue = m_modelRangeValues.get( id );
    final Object newModelRangeValue = item.mergeToModelRange( oldModelRangeValue, currentRangeValue );

    /* update the current state */
    m_modelRangeValues.put( id, newModelRangeValue );

    return item.toExchangeItems( newModelRangeValue );
  }

  public <T> String printItemValue( final AbstractServerItem<T> serverItem, final List<IExchangeItem> exItems, final Instant currentStartTime, final Instant currentEndTime )
  {
    final T modelRangeValue = serverItem.toValue( exItems );

    /* update the cached state */
    m_modelRangeValues.put( serverItem.getId(), modelRangeValue );

    /**
     * The value within the exchange item covers the full model range.
     * We want to restrict this to the current simulation range and only communicate this to HydPy
     */
    final T currentRangeValue = serverItem.restrictToCurrentRange( modelRangeValue, currentStartTime, currentEndTime );

    return serverItem.printValue( currentRangeValue );
  }
}