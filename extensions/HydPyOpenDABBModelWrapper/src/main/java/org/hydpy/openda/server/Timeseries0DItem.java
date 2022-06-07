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

import java.util.Arrays;

import org.joda.time.Instant;
import org.openda.exchange.timeseries.TimeSeries;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Timeseries0DItem extends AbstractSingleServerItem<Timeseries0D>
{
  public Timeseries0DItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public Timeseries0D parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText ) throws HydPyServerException
  {
    try
    {
      final double[] value = HydPyUtils.parseDoubleArray( valueText );

      final double[] times = HydPyUtils.buildTimes( value.length, startTime, stepSeconds, endTime );

      return new Timeseries0D( times, value, false );
    }
    catch( final Exception e )
    {
      final String message = String.format( "Exchange item '%s': failed to parse response of HydPy", getId() );
      throw new HydPyServerException( message, e );
    }
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final Timeseries0D value )
  {
    final TimeSeries timeSeries = new TimeSeries( value.getTimes(), value.getValues() );
    timeSeries.setId( getId() );
    return timeSeries;
  }

  @Override
  protected Timeseries0D toValue( final IExchangeItem exItem )
  {
    final TimeSeries timeSeries = (TimeSeries)exItem;
    final double[] times = timeSeries.getTimes();
    final double[] values = timeSeries.getValuesAsDoubles();
    return new Timeseries0D( times, values, false );
  }

  @Override
  public String printValue( final Timeseries0D value )
  {
    return HydPyUtils.printDoubleArray( value.getValues() );
  }

  @Override
  public Timeseries0D mergeToModelRange( final Timeseries0D initialRangeValue, final Timeseries0D currentRangeValue )
  {
    final double[] initialTimes = initialRangeValue.getTimes();
    final double[] initialValues = initialRangeValue.getValues();

    final double[] currentTimes = currentRangeValue.getTimes();
    final double[] currentValues = currentRangeValue.getValues();

    /* copy and merge arrays */
    final double[] modelTimes = Arrays.copyOf( initialTimes, initialTimes.length );

    final double[] modelValues = Arrays.copyOf( initialValues, initialValues.length );
    final int startIndex = HydPyUtils.indexOfMdj( initialTimes, currentTimes[0] );
    System.arraycopy( currentValues, 0, modelValues, startIndex, currentValues.length );

    return new Timeseries0D( modelTimes, modelValues, false );
  }

  @Override
  public Timeseries0D restrictToCurrentRange( final Timeseries0D modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    final TimeSeries timeSeries = new TimeSeries( modelRangeValue.getTimes(), modelRangeValue.getValues() );

    if( !currentStartTime.isBefore( currentEndTime ) )
      return new Timeseries0D( new double[0], new double[0], false );

    final TimeSeries subset = timeSeries.selectTimeSubset( HydPyUtils.instantToMjd( currentStartTime ), HydPyUtils.instantToMjd( currentEndTime ) );

    return toValue( subset );
  }

  @Override
  public Timeseries0D copy( final Timeseries0D value )
  {
    return value.copy();
  }
}