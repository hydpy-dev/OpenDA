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

import java.util.Arrays;
import java.util.NoSuchElementException;

import org.joda.time.Instant;
import org.openda.exchange.ArrayExchangeItem;
import org.openda.exchange.ArrayTimeInfo;
import org.openda.interfaces.IArray;
import org.openda.interfaces.IArrayTimeInfo;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.utils.Array;

/**
 * @author Gernot Belger
 */
public class Timeseries1DItem extends AbstractServerItem<Timeseries1D>
{
  public Timeseries1DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Timeseries1D parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    // [ [timeseris1] [timeseries2] [timeseries3] ... ] i.e. one ts per element
    final String arrayText = valueText//
        // TODO: should be handled in parser to avoid string copy
        .replace( "nan", "NaN" ) //
        .replace( " ", "" );

    final IArray array = HydPyUtils.parseArrayFromJson( arrayText );

    /* swap array dimensions */
    // FIXME: maybe directly handle by parser?
    final IArray swappedArray = HydPyUtils.swapArray2D( array );

    final int[] dimensions = swappedArray.getDimensions();

    final double[] times = HydPyUtils.buildTimes( dimensions[0], startTime, stepSeconds, endTime );

    return new Timeseries1D( times, swappedArray );
  }

  @Override
  public IExchangeItem toExchangeItem( final Timeseries1D timeseries )
  {
    final IArray array = timeseries.getValues();
    final double[] times = timeseries.getTimes();

    final IArrayTimeInfo timeInfo = new ArrayTimeInfo( times, 0 );

    final ArrayExchangeItem item = new ArrayExchangeItem( getId(), getRole() );
    item.setTimeInfo( timeInfo );
    item.setArray( array );

    return item;
  }

  @Override
  public Timeseries1D toValue( final IExchangeItem exItem )
  {
    final ArrayExchangeItem timeSeries = (ArrayExchangeItem)exItem;

    final double[] times = timeSeries.getTimes();
    final IArray values = timeSeries.getArray();

    return new Timeseries1D( times, values );
  }

  @Override
  public String printValue( final Timeseries1D timeseries )
  {
    final IArray array = timeseries.getValues();

    final IArray swappedArray = HydPyUtils.swapArray2D( array );

    return swappedArray.toString()//
        .replace( '{', '[' ) //
        .replace( '}', ']' ) //
        .replace( "NaN", "nan" );
  }

  @Override
  public Timeseries1D mergeToModelRange( final Timeseries1D initialRangeValue, final Timeseries1D currentRangeValue )
  {
    final double[] initialTimes = initialRangeValue.getTimes();
    final IArray initialValues = initialRangeValue.getValues();

    final double[] currentTimes = currentRangeValue.getTimes();
    final IArray currentValues = currentRangeValue.getValues();

    /* copy and merge arrays */
    final double[] modelTimes = Arrays.copyOf( initialTimes, initialTimes.length );

    final IArray modelValues = new Array( initialValues );

    final int startIndex = HydPyUtils.indexOfMdj( initialTimes, currentTimes[0] );
    final int endIndex = HydPyUtils.indexOfMdj( initialTimes, currentTimes[currentTimes.length - 1] );
    modelValues.setSlice( currentValues, 0, startIndex, endIndex );

    return new Timeseries1D( modelTimes, modelValues );
  }

  @Override
  public Timeseries1D restrictToCurrentRange( final Timeseries1D modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    final double[] modelTimes = modelRangeValue.getTimes();
    final IArray modelValues = modelRangeValue.getValues();
    final Instant[] modelInstants = HydPyUtils.mjdToInstant( modelTimes );

    final int startIndex = indexOfInstant( modelInstants, currentStartTime );
    final int endIndex = indexOfInstant( modelInstants, currentEndTime );

    final double[] currentTimes = Arrays.copyOfRange( modelTimes, startIndex, endIndex + 1 );

    final IArray slice = modelValues.getSlice( 0, startIndex, endIndex );

    return new Timeseries1D( currentTimes, slice );
  }

  // REMRK: leaving this utility here, as we get a very specific error message
  private static int indexOfInstant( final Instant[] instants, final Instant searchInstant )
  {
    final int index = Arrays.binarySearch( instants, searchInstant );
    if( index < 0 )
    {
      final String message = String.format( "Start or end time (%s) of current calculation range outside initial range provided by HydPy. Please check your aggregation times and/or time step.", searchInstant );
      throw new NoSuchElementException( message );
    }

    return index;
  }
}