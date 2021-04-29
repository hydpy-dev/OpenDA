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
import org.openda.interfaces.IPrevExchangeItem;
import org.openda.interfaces.IPrevExchangeItem.Role;
import org.openda.utils.Time;

/**
 * @author Gernot Belger
 */
final class Timeseries0DItem extends AbstractServerItem
{
  public Timeseries0DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Object parseValue( final String valueText )
  {
    return HydPyUtils.parseDoubleArray( valueText );
  }

  @Override
  public IPrevExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Object value )
  {
    final double[] values = (double[])value;

    final double[] times = new double[values.length];

    Instant time = startTime;

    final long stepMillis = stepSeconds * 1000;

    for( int i = 0; i < times.length; i++ )
    {
      // REMARK: HydPy thinks in time-intervals, hence we have one timestep less --> start with startTime + stepSeconds
      time = time.plus( stepMillis );

      times[i] = Time.milliesToMjd( time.getMillis() );
    }

    if( !time.equals( endTime ) )
      throw new IllegalStateException();

    final TimeSeries timeSeries = new TimeSeries( times, values );
    timeSeries.setId( getId() );
    return timeSeries;
  }

  @Override
  public String printValue( final IPrevExchangeItem exItem )
  {
    final TimeSeries timeSeries = (TimeSeries)exItem;

    final double[] mjdValues = timeSeries.getValuesAsDoubles();

    final double[] dateValues = new double[mjdValues.length];
    for( int i = 0; i < dateValues.length; i++ )
      dateValues[i] = Time.mjdToMillies( mjdValues[i] );

    return HydPyUtils.printDoubleArray( mjdValues );
  }
}