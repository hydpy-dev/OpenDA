/**
 * Copyright (c) 2019 by
 * - OpenDA Association
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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.QuantityInfo;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.ITime;
import org.openda.utils.ConfigTree;
import org.openda.utils.ObjectSupport;
import org.openda.utils.Results;
import org.openda.utils.Time;

/**
 * Holds the configuration information for the {@link SpatialNoiseModelInstance}.
 *
 * @author verlaanm
 * @author Gernot Belger
 */
final class SpatialNoiseModelConfiguration
{
  private final Collection<SpatialNoiseModelConfigurationItem> m_items;

  private final ITime m_timeHorizon;

  private final boolean m_suppressInternalStateSaving;

  public static SpatialNoiseModelConfiguration read( final File workingDir, final String[] arguments, final ITime modelHorizon )
  {
    final String configString = arguments[0];
    Results.putMessage( "configstring = " + configString );

    /*
     * now parse configuration
     */
    final ConfigTree conf = new ConfigTree( workingDir, configString );

    final ITime noiseHorizon = parseTimeHorizon( conf, modelHorizon );

    final boolean suppressInternalStateSaving = conf.getAsBoolean( "@suppressInternalStateSaving", false );

    // read geometry definitions
    final Map<String, ISpatialNoiseGeometry> geometries = readGeometries( conf, workingDir );

    final ConfigTree itemTrees[] = conf.getSubTrees( "noiseItem" );

    final Collection<SpatialNoiseModelConfigurationItem> items = new ArrayList<>( itemTrees.length );

    final double incrementTime = noiseHorizon.getStepMJD();

    for( final ConfigTree itemTree : itemTrees )
    {
      final SpatialNoiseModelConfigurationItem item = readItem( itemTree, geometries, incrementTime );
      items.add( item );
    }

    return new SpatialNoiseModelConfiguration( noiseHorizon, suppressInternalStateSaving, items );
  }

  /*
   * parse simulationTimespan
   * <simulationTimespan timeFormat="dateTimeString">201008241130,201008241140,...,201008242350</simulationTimespan>
   * <!--
   * <simulationTimespan timeFormat="mjd">48259.0,48259.125,...,48260.00</simulationTimespan>
   */
  private static ITime parseTimeHorizon( final ConfigTree conf, final ITime fallbackHorizon )
  {
    final String timespanString = conf.getContentString( "simulationTimespan" );

    double startTime = Double.NaN;
    double endTime = Double.NaN;
    double incrementTime = Double.NaN;

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
    final Time time = new Time( fallbackHorizon );

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

    if( !Double.isFinite( startTime ) || !Double.isFinite( endTime ) || !Double.isFinite( incrementTime ) )
      throw new RuntimeException( "Failed to determine time span for spatial noise model. either the model must have a defined time span or the 'simulationTimespan' must be set." );

    return new Time( startTime, endTime, incrementTime );
  }

  private static Map<String, ISpatialNoiseGeometry> readGeometries( final ConfigTree conf, final File workingDir )
  {
    final ConfigTree configs[] = conf.getSubTrees( "geometry" );

    final Map<String, ISpatialNoiseGeometry> geometries = new HashMap<>( configs.length );

    for( final ConfigTree config : configs )
    {
      final String id = config.getAsString( "@id", null );
      if( id == null )
        throw new RuntimeException( "Missing 'id' in geometry definition." );

      final ISpatialNoiseGeometry geometry = readGeometry( config, workingDir );
      geometries.put( id, geometry );
    }

    return geometries;
  }

  private static ISpatialNoiseGeometry readGeometry( final ConfigTree config, final File workingDir )
  {
    final CoordinatesType coordinatesType = SpatialNoiseUtils.parseCoordinatesType( config );
    final double horizontalCorrelationScale = SpatialNoiseUtils.parseHorizontalCorrelationScale( config );

    final ConfigTree child = config.getFirstChild();
    if( child == null )
      throw new RuntimeException( "Missing geomtry configuration" );

    final String factoryName = child.getAsString( "@factory", null );
    if( StringUtils.isBlank( factoryName ) )
      throw new RuntimeException( "Missing 'factory' attribute in geometry configuration" );

    final ISpatialNoiseGeometryFactory factory = (ISpatialNoiseGeometryFactory)ObjectSupport.createNewInstance( factoryName, ISpatialNoiseGeometryFactory.class );
    return factory.create( child, workingDir, coordinatesType, horizontalCorrelationScale );
  }

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
  private static SpatialNoiseModelConfigurationItem readItem( final ConfigTree itemConfig, final Map<String, ISpatialNoiseGeometry> geometries, final double incrementTime )
  {
    // id
    final String id = itemConfig.getAsString( "@id", "" ).trim();
    if( id.length() == 0 )
      throw new RuntimeException( "MapsNoiseModelInstance: missing id for item: " + itemConfig.toString() );
    System.out.println( "Id=" + id );

    // quantity
    final String quantityValue = itemConfig.getAsString( "@quantity", "unknown_quantity" ).trim();
    final String unitValue = itemConfig.getAsString( "@unit", "unknown_unit" ).trim();
    final QuantityInfo quantityInfo = new QuantityInfo( quantityValue, unitValue );

    // initialValue
    final double initialValue = itemConfig.getAsDouble( "@initialValue", 0.0 );

    // standardDeviation
    final double stdDeviation = itemConfig.getAsDouble( "@standardDeviation", 0.05 );

    // time correlation
    final double timeCorrelationScaleValue = itemConfig.getAsDouble( "@timeCorrelationScale", 1.0 );
    final String timeCorrelationScaleUnit = itemConfig.getAsString( "@timeCorrelationScaleUnit", "days" ).trim().toLowerCase();
    final double timeCorrelationScale = adjustTimeCorrectionScale( timeCorrelationScaleValue, timeCorrelationScaleUnit );

    // spatial correlation
    final String geometryRef = itemConfig.getAsString( "@geometry", null );

    // TODO: this breaks backwards compatibility to MapsNoiseModelFactory
    // we might instead fallback to a 'default' factory which reads data from the noiseItem-node and
    // but still creates the same instances as SpatialNoiseGridGeometryFactory.
    final ISpatialNoiseGeometry correlation = geometries.get( geometryRef );
    if( correlation == null )
      throw new RuntimeException( String.format( "noiseItem '%s': no geometry found with id '%s'", id, geometryRef ) );

    return new SpatialNoiseModelConfigurationItem( id, incrementTime, quantityInfo, initialValue, stdDeviation, timeCorrelationScale, correlation );
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

  public SpatialNoiseModelConfiguration( final ITime timeHorizon, final boolean suppressInternalStateSaving, final Collection<SpatialNoiseModelConfigurationItem> items )
  {
    m_timeHorizon = timeHorizon;
    m_suppressInternalStateSaving = suppressInternalStateSaving;
    m_items = items;
  }

  public ITime getTimeHorizon( )
  {
    return m_timeHorizon;
  }

  public Collection<SpatialNoiseModelConfigurationItem> getItems( )
  {
    return m_items;
  }

  public boolean isSuppressInternalStateSaving( )
  {
    return m_suppressInternalStateSaving;
  }
}