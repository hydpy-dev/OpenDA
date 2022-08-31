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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.list.primitive.MutableDoubleList;
import org.eclipse.collections.impl.factory.primitive.DoubleLists;
import org.joda.time.Instant;
import org.json.JSONArray;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.IArray;
import org.openda.utils.Array;
import org.openda.utils.Time;

import ch.randelshofer.fastdoubleparser.FastDoubleParser;

/**
 * Some static utils.
 *
 * @author Gernot Belger
 */
public final class HydPyUtils
{
  private static final double daysToSeconds = (60 * 60 * 24);

  private static final double secondsToDays = 1.0 / daysToSeconds;

  private HydPyUtils( )
  {
    throw new UnsupportedOperationException();
  }

  public static String getOptionalSystemProperty( final String key, final String defaultValue )
  {
    final String value = System.getenv( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return value;
  }

  public static String getRequiredProperty( final Properties properties, final String key )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      throw new RuntimeException( String.format( "Missing argument: %s", key ) );

    return value;
  }

  public static int getRequiredPropertyAsInt( final Properties properties, final String key )
  {
    final String value = getRequiredProperty( properties, key );
    return parseInt( key, value );
  }

  public static int getOptionalPropertyAsInt( final Properties properties, final String key, final int defaultValue )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return parseInt( key, value );
  }

  public static boolean getOptionalPropertyAsBoolean( final Properties properties, final String key, final boolean defaultValue )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    return Boolean.parseBoolean( value );
  }

  public static <E extends Enum<E>> E getOptionalPropertyAsEnum( final Properties properties, final String key, final E defaultValue )
  {
    final String value = properties.getProperty( key );
    if( StringUtils.isBlank( value ) )
      return defaultValue;

    @SuppressWarnings( "unchecked" ) final E result = (E)Enum.valueOf( defaultValue.getClass(), value );
    return result;
  }

  private static int parseInt( final String key, final String value )
  {
    try
    {
      return Integer.parseInt( value );
    }
    catch( final NumberFormatException e )
    {
      final String mesage = String.format( "Value '%s' of environment variable '%s' must be an integer.", value, key );
      throw new RuntimeException( mesage, e );
    }
  }

  public static double[] parseDoubleArray( final String text )
  {
    final StringTokenizer st = new StringTokenizer( text, "[], " );

    final int countTokens = st.countTokens();

    final double[] values = new double[countTokens];

    for( int i = 0; i < values.length; i++ )
    {
      final String token = st.nextToken();
      values[i] = parseDouble( token );
    }

    return values;
  }

  private static double parseDouble( final String token )
  {
    if( "nan".equals( token ) )
      return Double.NaN;

    return Double.parseDouble( token );
  }

  public static String printDoubleArray( final double[] doubles )
  {
    final StringBuilder buffer = new StringBuilder( "[" );

    for( int i = 0; i < doubles.length; i++ )
    {
      buffer.append( doubles[i] );

      if( i < doubles.length - 1 )
        buffer.append( ',' );
    }

    buffer.append( ']' );
    return buffer.toString();
  }

  public static String[] parseStringArray( final String text )
  {
    // TODO: ugly hack
    if( !text.startsWith( "[" ) )
      return new String[] { text };

    final JSONArray array = new JSONArray( text );

    final String[] values = new String[array.length()];

    for( int i = 0; i < array.length(); i++ )
      values[i] = array.getString( i );

    return values;
  }

  public static double durationToMjd( final long seconds )
  {
    return seconds * secondsToDays; // convert from millis to days and add offset for mjd
  }

  public static long mjdToDuration( final double mjd )
  {
    final long timeInSeconds = Math.round( mjd * daysToSeconds );
    return timeInSeconds;
  }

  /**
   * Builds and array of mjd times from a given number of steps, the start time and the step in seconds.
   *
   * @throws IllegalStateException,
   *           if the resulting last time does not matches the given expected end time.
   */
  public static double[] buildTimes( final int numSteps, final Instant startTime, final long stepSeconds, final Instant expectedEndTime )
  {
    final double[] times = new double[numSteps];
    Instant time = startTime;

    final long stepMillis = stepSeconds * 1000;

    for( int i = 0; i < times.length; i++ )
    {
      // REMARK: HydPy thinks in time-intervals, hence we have one timestep less --> start with startTime + stepSeconds
      time = time.plus( stepMillis );

      times[i] = Time.milliesToMjd( time.getMillis() );
    }

    if( !time.equals( expectedEndTime ) )
    {
      final long expectedSpan = expectedEndTime.getMillis() - startTime.getMillis();
      final long expectedNumSteps = expectedSpan / stepMillis;

      final String message = String.format( "Expected %d timesteps from HydPy, but got %d", expectedNumSteps, numSteps );
      throw new RuntimeException( message );
    }

    return times;
  }

  /**
   * Builds and array of time from a given startTime^+ step, end time and step in seconds.
   *
   * @throws IllegalStateException,
   *           if the end time is not exactly matched or if start time is not before end time.
   */
  public static Instant[] buildTimes( final Instant startTime, final Instant endTime, final long stepSeconds )
  {
    if( !startTime.isBefore( endTime ) )
      throw new IllegalStateException( "start time not before end time" );

    final List<Instant> times = new ArrayList<>();

    final long stepMillis = stepSeconds * 1000;
    Instant currentTime = startTime.plus( stepMillis );
    while( !endTime.isBefore( currentTime ) )
    {
      times.add( currentTime );

      if( currentTime.isEqual( endTime ) )
        return times.toArray( new Instant[times.size()] );

      currentTime = currentTime.plus( stepMillis );
    }

    throw new IllegalStateException( "end time was not exactly met." );
  }

  public static IArray swapArray2D( final IArray array )
  {
    final int[] dimensions = array.getDimensions();
    final int[] swappedDimensions = new int[] { dimensions[1], dimensions[0] };

    final Array swappedArray = new Array( swappedDimensions );

    final int[] iter = new int[2];
    final int[] swappedIter = new int[2];
    for( iter[0] = 0, swappedIter[1] = 0; //
        iter[0] < dimensions[0]; //
        iter[0]++, swappedIter[1]++ )
    {
      for( iter[1] = 0, swappedIter[0] = 0; //
          iter[1] < dimensions[1]; //
          iter[1]++, swappedIter[0]++ )
      {
        final double value = array.getValueAsDouble( iter );
        swappedArray.setValueAsDouble( swappedIter, value );
      }
    }

    return swappedArray;
  }

  public static Instant[] mjdToInstant( final double[] times )
  {
    final Instant[] instants = new Instant[times.length];

    for( int i = 0; i < instants.length; i++ )
      instants[i] = new Instant( TimeUtils.mjdToDate( times[i] ) );

    return instants;
  }

  public static double[] instantToMjd( final Instant[] instants )
  {
    final double[] mjd = new double[instants.length];

    for( int i = 0; i < instants.length; i++ )
      mjd[i] = instantToMjd( instants[i] );

    return mjd;
  }

  public static double instantToMjd( final Instant instant )
  {
    return TimeUtils.date2Mjd( instant.toDate() );
  }

  /**
   * Same as {@link #submitAndLogExceptions(ExecutorService, Callable)} for void return values.
   */
  public static Future<Void> submitAndLogExceptions( final ExecutorService executor, final Runnable callable )
  {
    final Callable<Void> wrapper = new Callable<Void>()
    {
      @Override
      public Void call( ) throws Exception
      {
        try
        {
          callable.run();
          return null;
        }
        catch( final Exception e )
        {
          e.printStackTrace();
          throw e;
        }
      }
    };

    return submitAndLogExceptions( executor, wrapper );
  }

  /**
   * Submits a {@link Callable} to the given executor.
   * Wraps the callable, so any exception will be logged.
   * This is necessary, as exceptions will typically only propagated once Future#get is called, which we sometimes don't do.
   */
  public static <RESULT> Future<RESULT> submitAndLogExceptions( final ExecutorService executor, final Callable<RESULT> callable )
  {
    return executor.submit( new Callable<RESULT>()
    {
      @Override
      public RESULT call( ) throws Exception
      {
        try
        {
          return callable.call();
        }
        catch( final Exception e )
        {
          e.printStackTrace();
          throw e;
        }
      }
    } );
  }

  public static int indexOfMdj( final double[] times, final double searchTime )
  {
    final int index = Arrays.binarySearch( times, searchTime );
    if( index < 0 )
      throw new NoSuchElementException();

    return index;
  }

  // REMARK: copy of the original parsing code of org.openda.utils.Array(String),
  // using a faster double parser and not using an ArrayList<Double>
  // Also uses Json syntax instead.
  public static IArray parseArrayFromJson( final String json )
  {
    int stringIndex = 0;

    // REMARK: rough estimate of initial size; HydPy often prints values with 20 places
    final MutableDoubleList valuesList = DoubleLists.mutable.withInitialCapacity( json.length() / 20 );

    final int[] counter = new int[20];
    final int[] dims = new int[20];

    int curDim = -1; // current dimension
    int rank = 0;

    while( stringIndex < json.length() )
    {
      final char charAt = json.charAt( stringIndex );
      if( charAt == '[' )
      {
        curDim++;
        if( curDim == rank )
          rank = curDim + 1;
        stringIndex++;
      }
      else if( charAt == ']' )
      {
        if( counter[curDim] > dims[curDim] )
          dims[curDim] = counter[curDim];
        counter[curDim] = 0;
        curDim--;
        if( curDim < -1 )
        {
          throw new RuntimeException( "Too many closing brackets in array at position=" + stringIndex + " in string=" + json );
        }
        stringIndex++;
      }
      else if( charAt == ',' )
      {
        counter[curDim]++;
        stringIndex++;
      }
      else
      {
        // try to find a number
        int indexEnd = json.indexOf( ']', stringIndex );
        // if a comma comes first
        final int indexComma = json.indexOf( ',', stringIndex );
        if( (indexComma >= 0) & (indexComma < indexEnd) )
        {
          indexEnd = indexComma;
        }

        final String numberString = json.substring( stringIndex, indexEnd );
        try
        {
          // final double value = Double.parseDouble( numberString );
          // REMARK: using this specialized double parser that if way faster than vanilla java
          // TODO: consider making a fork, that does not require to build substrings
          final double value = FastDoubleParser.parseDouble( numberString );
          valuesList.add( value );
          stringIndex = indexEnd;
        }
        catch( final NumberFormatException e )
        {
          throw new RuntimeException( "Problems parsing array at position=" + stringIndex + "while processing number " + numberString );
        }
      }
    }

    // store values
    final double[] values = valuesList.toArray();

    // store dimensions
    final int[] dimensions = new int[rank];
    for( int i = 0; i < rank; i++ )
      dimensions[i] = dims[i] + 1;

    return new Array( values, dimensions, false );
  }

  public static void zipConditionsDirectory( final Path sourceDir, final Path targetZipFile ) throws IOException
  {
    // REMARK: we know that hydpy only ever writes a flat list of files
    final List<Path> files = Files.list( sourceDir ).collect( Collectors.toList() );
    try( final ArchiveOutputStream o = new ZipArchiveOutputStream( Files.newOutputStream( targetZipFile ) ) )
    {
      for( final Path sourceFile : files )
      {
        if( !Files.isRegularFile( sourceFile ) )
          throw new IllegalStateException();

        // maybe skip directories for formats like AR that don't store directories
        final ArchiveEntry entry = o.createArchiveEntry( sourceFile, sourceFile.getFileName().toString() );
        o.putArchiveEntry( entry );

        FileUtils.copyFile( sourceFile.toFile(), o );

        o.closeArchiveEntry();
      }
    }
  }

  public static void unzipConditions( final Path sourceZipFile, final Path targetDir ) throws IOException
  {
    try( final ArchiveInputStream i = new ZipArchiveInputStream( Files.newInputStream( sourceZipFile ) ) )
    {
      ArchiveEntry entry = null;
      while( (entry = i.getNextEntry()) != null )
      {
        if( !i.canReadEntryData( entry ) )
          throw new IllegalStateException();

        final Path targetFile = targetDir.resolve( entry.getName() );
        if( !targetFile.normalize().startsWith( targetDir ) )
          throw new IOException( "Bad zip entry" );

        try( final OutputStream o = new BufferedOutputStream( Files.newOutputStream( targetFile ) ) )
        {
          IOUtils.copy( i, o );
        }
      }
    }
  }
}