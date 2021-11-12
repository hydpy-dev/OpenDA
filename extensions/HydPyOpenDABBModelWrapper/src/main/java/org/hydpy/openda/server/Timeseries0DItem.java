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

import org.joda.time.Instant;
import org.openda.exchange.timeseries.TimeSeries;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Timeseries0DItem extends AbstractServerItem<double[]>
{
  public Timeseries0DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public double[] parseValue( final String valueText )
  {
    return HydPyUtils.parseDoubleArray( valueText );
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final double[] value )
  {
    final double[] times = HydPyUtils.buildTimes( value.length, startTime, stepSeconds, endTime );

    final TimeSeries timeSeries = new TimeSeries( times, value );
    timeSeries.setId( getId() );
    return timeSeries;
  }

  @Override
  public double[] toValue( final Instant startTime, final Instant endTime, final long stepSeconds, final IExchangeItem exItem )
  {
    final TimeSeries timeSeries = (TimeSeries)exItem;

//    final TimeSeries subsset = timeSeries.selectTimeSubset( HydPyUtils.instantToMjd( startTime ), HydPyUtils.instantToMjd( endTime ) );
//    return subsset.getValuesAsDoubles();

    return timeSeries.getValuesAsDoubles();
  }

  @Override
  public String printValue( final double[] value )
  {
    return HydPyUtils.printDoubleArray( value );
  }
}