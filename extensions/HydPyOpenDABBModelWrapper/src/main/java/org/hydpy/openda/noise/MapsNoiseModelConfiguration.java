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
package org.hydpy.openda.noise;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.hydpy.openda.noise.SpatialCorrelationStochVector.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.exchange.QuantityInfo;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.ITime;
import org.openda.utils.Array;
import org.openda.utils.ConfigTree;
import org.openda.utils.Results;
import org.openda.utils.Time;

/**
 * @author Gernot Belger
 */
class MapsNoiseModelConfiguration
{
  private final Collection<MapsNoiseModelConfigurationItem> m_items;

  private final ITime m_timeHorizon;

  public static MapsNoiseModelConfiguration read( final File workingDir, final String[] arguments, final ITime timeHorizon )
  {
    final String configString = arguments[0];
    Results.putMessage( "configstring = " + configString );

    /*
     * now parse configuration
     */
    final ConfigTree conf = new ConfigTree( workingDir, configString );

    /*
     * parse simulationTimespan
     * <simulationTimespan timeFormat="dateTimeString">201008241130,201008241140,...,201008242350</simulationTimespan>
     * <!--
     * <simulationTimespan timeFormat="mjd">48259.0,48259.125,...,48260.00</simulationTimespan>
     */
    final String timespanString = conf.getContentString( "simulationTimespan" );
    double startTime = 0.0;
    double endTime = 100.0;
    double incrementTime = 1.0;
    if( timespanString != null )
    {
      Results.putMessage( "analysisTimes=" + timespanString );
      final String timeFormat = conf.getAsString( "simulationTimespan@timeFormat", "dateTimeString" );
      if( !timespanString.contains( "..." ) )
        throw new RuntimeException( "TimeSeriesNoiseModel expects a simulationTimeSpan formatted as eg 201008241130,201008241140,...,201008242350" );

      Time[] simulationSequence;
      if( timeFormat.equals( "dateTimeString" ) )
        simulationSequence = Time.Mjds2Times( TimeUtils.dateTimeSequenceString2Mjd( timespanString ) );
      else
      { // use Mjd
        simulationSequence = Time.Mjds2Times( TimeUtils.MjdSequenceString2Mjd( timespanString ) );
      }

      final int nTimes = simulationSequence.length;
      startTime = simulationSequence[0].getBeginMJD();
      endTime = simulationSequence[nTimes - 1].getBeginMJD();
      incrementTime = simulationSequence[1].getBeginMJD() - simulationSequence[0].getBeginMJD();
    }

    // overrule with external settings
    final Time time = new Time( timeHorizon );

    if( !Double.isInfinite( time.getBeginMJD() ) )
      startTime = time.getBeginMJD();

    if( !Double.isInfinite( time.getEndMJD() ) )
      endTime = time.getEndMJD();

    // Note: if the timeHorizon is set from outside, then the timeHorizon can have a timeStep set to NaN,
    // if the method modelInstance.getTimeHorizon() returns a timeHorizon without a valid timeStep, i.e. if the
    // modelInstance is not able to determine the timeStep of the model. This is the case when using
    // for instance the BBModelInstance. Currently the only workaround for this is as follows:
    // In addition to the timeHorizon set from outside, configure a dummy simulationTimeSpan in the noise model
    // config file. Then the difference between the first two times is used as the timeStep for the noise model.
    if( !Double.isNaN( time.getStepMJD() ) )
      incrementTime = time.getStepMJD();

    final ITime adjustedTimeHorizon = new Time( startTime, endTime, incrementTime );

    /*
     * Parse input per item
     * <noiseItem id="windU"
     * quantity="wind-u"
     * unit="m/s" height="10.0"
     * standardDeviation="1.0"
     * timeCorrelationScale="12.0" timeCorrelationScaleUnit="hours"
     * initialValue="0.0"
     * horizontalCorrelationScale="500" horizontalCorrelationScaleUnit="km" >
     * <grid type="cartesian" coordinates="wgs84" separable="true" >
     * <x>-5,0,...,5</x>
     * <y>50,55,...,60</y>
     * </grid>
     * </noiseItem>
     */
    final ConfigTree itemTrees[] = conf.getSubTrees( "noiseItem" );

    final Collection<MapsNoiseModelConfigurationItem> items = new ArrayList<>( itemTrees.length );

    for( final ConfigTree itemTree : itemTrees )
    {
      final MapsNoiseModelConfigurationItem item = readItem( itemTree, incrementTime );
      items.add( item );
    }

    return new MapsNoiseModelConfiguration( adjustedTimeHorizon, items );
  }

  private static MapsNoiseModelConfigurationItem readItem( final ConfigTree itemConfig, final double incrementTime )
  {
    // id
    final String id = itemConfig.getAsString( "@id", "" ).trim();
    if( id.length() == 0 )
      throw new RuntimeException( "MapsNoiseModelInstance: missing id for item: " + itemConfig.toString() );
    System.out.println( "Id=" + id );

    // create empty item
//    final ArrayExchangeItem tempItem = new ArrayExchangeItem( id, Role.Output );

    // quantity
    final String quantityValue = itemConfig.getAsString( "@quantity", "unknown_quantity" ).trim();
    // unit
    final String unitValue = itemConfig.getAsString( "@unit", "unknown_unit" ).trim();

    final QuantityInfo quantityInfo = new QuantityInfo( quantityValue, unitValue );

//    tempItem.setQuantityInfo( tempQ );

    /*
     * spatial grid
     * <grid type="cartesian" coordinates="wgs84" separable="true">
     * <x>-5,0,...,5</x>
     * <y>50,55,...,60</y>
     * </grid>
     */
    final String gridValueTypeValue = itemConfig.getAsString( "grid@type", "cartesian" );
    // FIXME: arg....!
    if( !gridValueTypeValue.toLowerCase().startsWith( "cart" ) )
      throw new RuntimeException( "MapsNoisModelInstance - grid@type : only type cartesian is supported, but found " + gridValueTypeValue );

    final String coordsTypeValue = itemConfig.getAsString( "grid@coordinates", CoordinatesType.WGS84.name() ).toUpperCase();
    final CoordinatesType coordsType = CoordinatesType.valueOf( coordsTypeValue );

    final boolean separable = itemConfig.getAsBoolean( "grid@separable", true );

    // grid values
    final String xValuesString = itemConfig.getContentString( "grid/x" );
    final String yValuesString = itemConfig.getContentString( "grid/y" );
    final double[] x = parseGridOneDim( xValuesString, "x-grid" );
    final double[] y = parseGridOneDim( yValuesString, "y-grid" );

    System.out.println( "x=" + xValuesString );
    System.out.println( "y=" + yValuesString );

    final Array latitudeArray = new Array( y, new int[] { y.length }, true );
    final Array longitudeArray = new Array( x, new int[] { x.length }, true );
    final int latitudeValueIndices[] = new int[] { 1 }; // we use the order time, lat, lon according to CF
    final int longitudeValueIndices[] = new int[] { 2 };
    final QuantityInfo latitudeQuantityInfo = null; // TODO
    final QuantityInfo longitudeQuantityInfo = null;
    final ArrayGeometryInfo geometryInfo = new ArrayGeometryInfo( latitudeArray, latitudeValueIndices, latitudeQuantityInfo, longitudeArray, longitudeValueIndices, longitudeQuantityInfo, null, null, null );

    // initialValue
    final double initialValue = itemConfig.getAsDouble( "@initialValue", 0.0 );

    // standardDeviation
    final double stdDeviation = itemConfig.getAsDouble( "@standardDeviation", 0.05 );

    // time correlation
    final double timeCorrelationScaleValue = itemConfig.getAsDouble( "@timeCorrelationScale", 1.0 );
    final String timeCorrelationScaleUnit = itemConfig.getAsString( "@timeCorrelationScaleUnit", "days" ).trim().toLowerCase();
    final double timeCorrelationScale = adjustTimeCorrectionScale( timeCorrelationScaleValue, timeCorrelationScaleUnit );

    // spatial correlation
    final double horizontalCorrelationScaleValue = itemConfig.getAsDouble( "@horizontalCorrelationScale", 0.0 );
    final String horizontalCorrelationScaleUnit = itemConfig.getAsString( "@horizontalCorrelationScaleUnit", "m" ).trim().toLowerCase();
    final double horizontalCorrelationScale = adjustHorizontalCorrelationScale( horizontalCorrelationScaleValue, horizontalCorrelationScaleUnit );

    return new MapsNoiseModelConfigurationItem( id, incrementTime, quantityInfo, coordsType, geometryInfo, separable, initialValue, stdDeviation, timeCorrelationScale, horizontalCorrelationScale, x, y );
  }

  private static double adjustHorizontalCorrelationScale( final double horizontalCorrelationScale, final String horizontalCorrelationScaleUnit )
  {
    switch( horizontalCorrelationScaleUnit )
    {
      case "m":
        return horizontalCorrelationScale * 1.0;

      case "km":
        return horizontalCorrelationScale * 1000.;

      case "cm":
        return horizontalCorrelationScale * 0.01;

      default:
        throw new RuntimeException( "unknown horizontalCorrelationScaleUnit." + " Expected (m,km,cm),but found " + horizontalCorrelationScaleUnit );
    }
  }

  private static double adjustTimeCorrectionScale( final double timeCorrelationScaleValue, final String timeCorrelationScaleUnit )
  {
    switch( timeCorrelationScaleUnit )
    {
      case "days":
        return timeCorrelationScaleValue * 1.0;

      case "hours":
        return timeCorrelationScaleValue / 24.0;

      case "minutes":
        return timeCorrelationScaleValue / 24.0 / 60.0;

      case "seconds":
        return timeCorrelationScaleValue / 24.0 / 60.0 / 60.0;

      default:
        throw new RuntimeException( "unknown timeCorrelationScaleUnit. Expected (days,hours,minutes,seconds),but found " + timeCorrelationScaleUnit );
    }
  }

  /**
   * Parse a string with a list of numbers eg 1.0,2.0,3.0
   * or 1.0,2.0,...,10.0 for sequence with fixed steps
   * into a double[] array
   *
   * @param valuesString
   *          String containing the double values
   * @return values as doubles
   */
  private static double[] parseGridOneDim( String valuesString, final String gridLabel )
  {
    if( valuesString.equals( "" ) )
    { // TODO add 0,5,...20
      throw new RuntimeException( String.format( "MapsNoisModelInstance: missing %s", gridLabel ) );
    }

    try
    {
      valuesString = valuesString.trim();
      final String parts[] = valuesString.split( "," );
      double[] result = null;
      if( !valuesString.contains( "..." ) )
      { // eg 1.0,2.0,3.0
        final int n = parts.length;
        result = new double[n];
        for( int j = 0; j < n; j++ )
        {
          result[j] = Double.parseDouble( parts[j] );
        }
      }
      else
      {
        final double start = Double.parseDouble( parts[0] );
        final double step = Double.parseDouble( parts[1] ) - start;
        final double stop = Double.parseDouble( parts[3] );
        final int n = (int)Math.round( (stop - start) / step ) + 1;
        result = new double[n];
        for( int i = 0; i < n; i++ )
        {
          result[i] = start + i * step;
        }
      }

      return result;
    }
    catch( final NumberFormatException e )
    {
      throw new RuntimeException( String.format( "Problems parsing %s", gridLabel ), e );
    }
  }

  public MapsNoiseModelConfiguration( final ITime timeHorizon, final Collection<MapsNoiseModelConfigurationItem> items )
  {
    m_timeHorizon = timeHorizon;
    m_items = items;
  }

  public ITime getTimeHorizon( )
  {
    return m_timeHorizon;
  }

  public Collection<MapsNoiseModelConfigurationItem> getItems( )
  {
    return m_items;
  }
}