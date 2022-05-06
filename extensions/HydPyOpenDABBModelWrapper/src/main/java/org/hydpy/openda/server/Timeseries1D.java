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

import org.apache.commons.lang3.Validate;
import org.joda.time.Instant;
import org.openda.interfaces.IArray;
import org.openda.utils.Array;

/**
 * @author Gernot Belger
 */
final class Timeseries1D
{
  private final double[] m_times;

  private final IArray m_values;

  public Timeseries1D( final double[] times, final IArray values )
  {
    final int[] dimensions = values.getDimensions();
    Validate.isTrue( dimensions.length == 2 );
    Validate.isTrue( dimensions[0] == times.length, "First dimension of array must be same as the number of timesteps" );

    m_times = times;
    m_values = values;
  }

  public double[] getTimes( )
  {
    return m_times;
  }

  public IArray getValues( )
  {
    return m_values;
  }

  public static Timeseries1D fromHydPy( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
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

  public String printHydPy( )
  {
    final IArray swappedArray = HydPyUtils.swapArray2D( m_values );

    return swappedArray.toString()//
        .replace( '{', '[' ) //
        .replace( '}', ']' ) //
        .replace( "NaN", "nan" );
  }

  public Timeseries1D insert( final Timeseries1D other )
  {
    final double[] currentTimes = other.getTimes();
    final IArray currentValues = other.getValues();

    /* copy and merge arrays */
    final double[] mergedTimes = Arrays.copyOf( m_times, m_times.length );

    final IArray mergedValues = new Array( m_values );

    final int startIndex = HydPyUtils.indexOfMdj( m_times, currentTimes[0] );
    final int endIndex = HydPyUtils.indexOfMdj( m_times, currentTimes[currentTimes.length - 1] );
    mergedValues.setSlice( currentValues, 0, startIndex, endIndex );

    return new Timeseries1D( mergedTimes, mergedValues );
  }

  public Timeseries1D restrictToRange( final Instant currentStartTime, final Instant currentEndTime )
  {
    final double[] modelTimes = getTimes();
    final IArray modelValues = getValues();
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