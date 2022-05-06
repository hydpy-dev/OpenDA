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

import org.joda.time.Instant;
import org.openda.exchange.ArrayExchangeItem;
import org.openda.exchange.ArrayTimeInfo;
import org.openda.interfaces.IArray;
import org.openda.interfaces.IArrayTimeInfo;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
public class Timeseries1DItem extends AbstractSingleServerItem<Timeseries1D>
{
  public Timeseries1DItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public Timeseries1D parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    return Timeseries1D.fromHydPy( startTime, endTime, stepSeconds, valueText );
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final Timeseries1D timeseries )
  {
    final IArray array = timeseries.getValues();
    final double[] times = timeseries.getTimes();

    final IArrayTimeInfo timeInfo = new ArrayTimeInfo( times, 0 );

    final ArrayExchangeItem item = new ArrayExchangeItem( id, role );
    item.setTimeInfo( timeInfo );
    item.setArray( array );

    return item;
  }

  @Override
  protected Timeseries1D toValue( final IExchangeItem exItem )
  {
    final ArrayExchangeItem timeSeries = (ArrayExchangeItem)exItem;

    final double[] times = timeSeries.getTimes();
    final IArray values = timeSeries.getArray();

    return new Timeseries1D( times, values, true );
  }

  @Override
  public String printValue( final Timeseries1D timeseries )
  {
    return timeseries.printHydPy();
  }

  @Override
  public Timeseries1D mergeToModelRange( final Timeseries1D initialRangeValue, final Timeseries1D currentRangeValue )
  {
    return initialRangeValue.insert( currentRangeValue );
  }

  @Override
  public Timeseries1D restrictToCurrentRange( final Timeseries1D modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    return modelRangeValue.restrictToRange( currentStartTime, currentEndTime );
  }

  @Override
  public Timeseries1D copy( final Timeseries1D value )
  {
    return value.copy();
  }
}