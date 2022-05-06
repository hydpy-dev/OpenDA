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
  private final Map<String, Object> m_firstValues;

  public HydPyExchangeCache( final Map<String, Object> firstValues )
  {
    m_firstValues = new HashMap<>( firstValues );
  }

  public List<IExchangeItem> parseItemValue( final String id, final AbstractServerItem<Object> item, final Object currentRangeValue )
  {
    /**
     * currentRange value is the value within the current range, as received from HydPy
     * To OpenDa we communicate a value that covers the full (aka model) range.
     */
    final Object initalRangeValue = m_firstValues.get( id );
    final Object modelRangeValue = item.mergeToModelRange( initalRangeValue, currentRangeValue );
// FIXME: should we not update the current state of the full object?
    return item.toExchangeItems( modelRangeValue );
  }

  public <T> String printItemValue( final AbstractServerItem<T> serverItem, final List<IExchangeItem> exItems, final Instant currentStartTime, final Instant currentEndTime )
  {
    final T modelRangeValue = serverItem.toValue( exItems );

    /**
     * The value within the exchange item covers the full model range.
     * We want to restrict this to the current simulation range and only communicate this to HydPy
     */
    final T currentRangeValue = serverItem.restrictToCurrentRange( modelRangeValue, currentStartTime, currentEndTime );

    return serverItem.printValue( currentRangeValue );
  }
}