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
import java.util.Map;

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
public class Timeseries1DItem extends AbstractServerItem<IArray>
{
  public Timeseries1DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public IArray parseValue( final String valueText )
  {
    // [ [timeseris1] [timeseries2] [timeseries3] ... ] i.e. one ts per element
    final String arrayText = valueText//
        .replace( '[', '{' ) //
        .replace( ']', '}' ) //
        .replace( " ", "" );

    final Array array = new Array( arrayText );

    /* swap array dimensions */
    final IArray swappedArray = HydPyUtils.swapArray2D( array );

    final int[] dimensions = array.getDimensions();
    if( dimensions.length != 2 )
      throw new IllegalStateException( "Values of a Timeseries1D must be a 2-dimensional arrays" );

    return swappedArray;
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final IArray array )
  {
    final int[] dimensions = array.getDimensions();
    if( dimensions.length != 2 )
      throw new IllegalStateException();

    final double[] times = HydPyUtils.buildTimes( dimensions[0], startTime, stepSeconds, endTime );

    final IArrayTimeInfo timeInfo = new ArrayTimeInfo( times, 0 );

    final ArrayExchangeItem item = new ArrayExchangeItem( getId(), getRole() );
    item.setTimeInfo( timeInfo );
    item.setArray( array );

    return item;
  }

  @Override
  public IArray toValue( final Instant startTime, final Instant endTime, final long stepSeconds, final IExchangeItem exItem )
  {
    final ArrayExchangeItem timeSeries = (ArrayExchangeItem)exItem;

//    final Instant[] targetTimes = HydPyUtils.buildTimes( startTime, endTime, stepSeconds );

//    final ITimeInfo timeInfo = timeSeries.getTimeInfo();
//    final double[] times = timeInfo.getTimes();
//    final Instant[] instants = HydPyUtils.mjdToInstant( times );

//    final int[] timeIndices = findTimeIndices( instants, targetTimes );
//    final int startIndex = timeIndices[0];
//    final int endIndex = timeIndices[timeIndices.length - 1];

    final IArray values = timeSeries.getArray();

//    final IArray slice = values.getSlice( 0, startIndex, endIndex );

//    return slice;

    return values;
  }

  private int[] findTimeIndices( final Instant[] times, final Instant[] targetTimes )
  {
    final Map<Instant, Integer> index = new HashMap<>();
    for( int i = 0; i < times.length; i++ )
      index.put( times[i], i );

    final int[] indices = new int[targetTimes.length];
    for( int i = 0; i < indices.length; i++ )
    {
      final Integer pos = index.get( targetTimes[i] );
      if( pos == null )
        throw new IllegalStateException();

      indices[i] = pos;

      if( i > 0 && pos != indices[i - 1] + 1 )
        throw new IllegalStateException( "times not correctly ordered" );
    }

    return indices;
  }

  @Override
  public String printValue( final IArray array )
  {
    final IArray swappedArray = HydPyUtils.swapArray2D( array );

    return swappedArray.toString()//
        .replace( '{', '[' ) //
        .replace( '}', ']' );
  }
}