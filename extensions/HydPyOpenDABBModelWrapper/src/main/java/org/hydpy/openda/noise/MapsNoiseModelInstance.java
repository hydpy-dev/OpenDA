/* FIXME
 * Copyright (c) 2019 OpenDA Association
 * All rights reserved.
 *
 * This file is part of OpenDA.
 *
 * OpenDA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * OpenDA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with OpenDA.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.hydpy.openda.noise;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.openda.exchange.ArrayExchangeItem;
import org.openda.exchange.TimeInfo;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IInstance;
import org.openda.interfaces.IModelState;
import org.openda.interfaces.IObservationDescriptions;
import org.openda.interfaces.IPrevExchangeItem;
import org.openda.interfaces.IPrevExchangeItem.Role;
import org.openda.interfaces.IResultWriter;
import org.openda.interfaces.IStochModelFactory.OutputLevel;
import org.openda.interfaces.IStochModelInstance;
import org.openda.interfaces.IStochVector;
import org.openda.interfaces.ITime;
import org.openda.interfaces.ITreeVector;
import org.openda.interfaces.IVector;
import org.openda.utils.Array;
import org.openda.utils.Instance;
import org.openda.utils.Results;
import org.openda.utils.StochTreeVector;
import org.openda.utils.Time;
import org.openda.utils.TreeVector;
import org.openda.utils.Vector;

/**
 * FIXME: comment
 * Module for generating noise for timeseries of 2D maps with Gaussian distribution and
 * exponential time-correlation. The spatial correlation is Gaussian
 * This module is intended for coupling to another model, usually to extend a physical model
 * with some uncertainties.
 * To speed up calculations the coordinates can be treated as separable. This means that correlations
 * are computed for both spatial directions separately. This is much faster, but is only approximate for
 * spherical coordinates, especially near the poles.
 * Assumes the following structure for the input file:
 * <?xml version="1.0" encoding="UTF-8"?>
 * <mapsNoiseModelConfig>
 * <simulationTimespan timeFormat="dateTimeString">201008241200,201008241210,...,201008242350</simulationTimespan>
 * <noiseItem id="windU" quantity="wind-u" unit="m/s" height="10.0"
 * standardDeviation="1.0" timeCorrelationScale="12.0" timeCorrelationScaleUnit="hours"
 * initialValue="0.0" horizontalCorrelationScale="500" horizontalCorrelationScaleUnit="km" >
 * <grid type="cartesian" coordinates="wgs84" separable="true">
 * <x>-5,0,...,5</x>
 * <y>50,55,...,60</y>
 * </grid>
 * </noiseItem>
 * </mapsNoiseModelConfig>
 *
 * @author verlaanm
 */
public class MapsNoiseModelInstance extends Instance implements IStochModelInstance
{
  private final Map<String, MapsNoiseModelItem> m_items = new HashMap<>();

  private final int m_instanceNumber;

  private final OutputLevel m_outputLevel;

  // Internal state of the model
  private final TreeVector m_state;

  // System noise for Kalman filtering
  private final StochTreeVector m_sysNoiseIntensity;

  private final TreeVector m_timeCorrelationPerTimeStep;

  private final ITime m_timeHorizon;

  private boolean m_autoNoise = false;

  private double m_curentTime;

  private int m_timeStep; // integer value is used for testing equality and counting

  public MapsNoiseModelInstance( final int instanceNumber, final OutputLevel outputLevel, final MapsNoiseModelConfiguration configuration )
  {
    m_instanceNumber = instanceNumber;
    m_outputLevel = outputLevel;

    m_timeHorizon = configuration.getTimeHorizon();

    // start at initial time
    m_curentTime = m_timeHorizon.getBeginTime().getMJD();
    m_timeStep = 0; // counter starts at 0

    m_state = new TreeVector( "state" );

    m_timeCorrelationPerTimeStep = new TreeVector( "timeCorrelationPerTimeStep" );
    m_sysNoiseIntensity = new StochTreeVector( "systemNoise" );

    final Collection<MapsNoiseModelConfigurationItem> items = configuration.getItems();
    for( final MapsNoiseModelConfigurationItem item : items )
    {
      final String id = item.getId();

      final ArrayExchangeItem outputItem = new ArrayExchangeItem( id, Role.Output );
      outputItem.setQuantityInfo( item.getQuantityInfo() );
      outputItem.setGeometryInfo( item.getGeometryInfo() );

      // initialValue
      final int xLength = item.getSizeX();
      final int yLength = item.getSizeY();
      final double initialValue = item.getInitialValue();
      final TreeVector statePart = new TreeVector( id, new Vector( xLength * yLength ), yLength, xLength );
      statePart.setConstant( initialValue );
      m_state.addChild( statePart );

      final TreeVector alphaPart = statePart.clone();
      alphaPart.setConstant( item.getAlpha() );
      m_timeCorrelationPerTimeStep.addChild( alphaPart );

      // stochVector for generation of white noise
      final IStochVector systemNoiseChild = createSystemNoiseChild( item );
      m_sysNoiseIntensity.addChild( systemNoiseChild );

      final MapsNoiseModelItem modelItem = new MapsNoiseModelItem( id, item, outputItem );
      m_items.put( id, modelItem );
    }
  }

  private IStochVector createSystemNoiseChild( final MapsNoiseModelConfigurationItem item )
  {
    final double stdWhiteNoise = item.getStandardWhiteNoise();
    if( item.isSeparable() )
    {
      final SpatialCorrelationCovariance xCovarianceMatrix = item.getSpatialCorrelationCovarianceX();
      final SpatialCorrelationCovariance yCovarianceMatrix = item.getSpatialCorrelationCovarianceY();

      return new Spatial2DCorrelationStochVector( stdWhiteNoise, xCovarianceMatrix, yCovarianceMatrix );
    }

    final SpatialCorrelationCovariance covarianceMatrix = item.getSpatialCorrelationCovariance();
    return new SpatialCorrelationStochVector( stdWhiteNoise, covarianceMatrix );
  }

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    // REMARK: post-constructor does not make sense, this class only always gets instantiated by its factory by a non-default constructor.
    throw new UnsupportedOperationException();
  }

  @Override
  public IPrevExchangeItem getExchangeItem( final String exchangeItemID )
  {
    return m_items.get( exchangeItemID ).getOutputSeries();
  }

  @Override
  public ITime getTimeHorizon( )
  {
    return m_timeHorizon;
  }

  @Override
  public ITime getCurrentTime( )
  {
    return new Time( m_curentTime );
  }

  @Override
  public IVector getState( )
  {
    return m_state.clone();
  }

  @Override
  public void axpyOnState( final double alpha, final IVector vector )
  {
    m_state.axpy( alpha, vector ); // nothing special for this model
  }

  @Override
  public void compute( final ITime targetTime )
  {
    final double incrementTime = m_timeHorizon.getStepMJD();
    final int nsteps = (int)Math.round( (targetTime.getMJD() - m_curentTime) / incrementTime );
    if( nsteps < 0 )
      throw new RuntimeException( "MapNoisemodel cannot compute backwards in time, ie target<currentTime" );

    final Map<String, Array> allValues = new HashMap<>();

    for( final MapsNoiseModelItem item : m_items.values() )
    {
      final String id = item.getId();
      final int xn = item.getSizeX();
      final int yn = item.getSizeY();

      final Array state = new Array( new int[] { nsteps + 1, yn, xn } );

      // save state for output
      final ITreeVector statePart = m_state.getSubTreeVector( id );
      state.setSlice( statePart.getValues(), 0, 0, 0 );

      allValues.put( id, state );
    }

    final double[] times = new double[nsteps + 1];
    times[0] = m_curentTime;

    for( int nextIndex = 1; nextIndex <= nsteps; nextIndex++ )
    {
      m_curentTime += incrementTime;
      times[nextIndex] = m_curentTime;
      m_timeStep++;

      // --> AR(1)
      // System.out.print("step :"+i+" ");
      m_state.pointwiseMultiply( m_timeCorrelationPerTimeStep );

      // add system noise
      if( m_autoNoise )
      {
        final IVector w = m_sysNoiseIntensity.createRealization();
        // Results.putProgression("> w="+w+" sqrt(t_step)="+Math.sqrt(t_step));
        Results.putValue( "white_noise", w, w.getSize(), "noisemodel_2d_" + m_instanceNumber, IResultWriter.OutputLevel.Verbose, IResultWriter.MessageType.Step );
        m_state.axpy( 1.0, w );
      }

      if( m_outputLevel != OutputLevel.Suppress )
      {
        Results.putValue( "model_time", m_curentTime, 1, "noisemodel_2d_" + m_instanceNumber, IResultWriter.OutputLevel.Verbose, IResultWriter.MessageType.Step );
        Results.putValue( "x", m_state, m_state.getSize(), "noisemodel_2d_" + m_instanceNumber, IResultWriter.OutputLevel.Verbose, IResultWriter.MessageType.Step );
      }

      // save state for output
      for( final MapsNoiseModelItem item : m_items.values() )
      {
        final String id = item.getId();
        final ITreeVector statePart = m_state.getSubTreeVector( id );
        allValues.get( id ).setSlice( statePart.getValues(), 0, nextIndex, nextIndex );
      }
    }

    // output storage set times and values for this compute
    for( final MapsNoiseModelItem item : m_items.values() )
    {
      final ArrayExchangeItem exItem = item.getOutputSeries();
      exItem.setArray( allValues.get( item.getId() ) );
      exItem.setTimeInfo( new TimeInfo( times ) );
    }
  }

  @Override
  public IVector[] getObservedLocalization( final IObservationDescriptions observationDescriptions, final double distance )
  {
    return null;
  }

  @Override
  public IModelState saveInternalState( )
  {
    final boolean coldStart = m_timeStep == -1;
    return new MapsNoiseModelState( coldStart, m_curentTime, m_timeStep, m_state.clone() );
  }

  @Override
  public void restoreInternalState( final IModelState savedInternalState )
  {
    final MapsNoiseModelState saveState = (MapsNoiseModelState)savedInternalState;

    final IVector state = saveState.getState();

    try
    {
      m_state.setValues( state.getValues() );
    }
    catch( final ArrayIndexOutOfBoundsException e )
    {
      // ODA-615 Give error message on ArrayIndexOutOfBoundsException when restoring internal state in OpenDA in FEWS
      if( m_state.getValues().length != state.getValues().length )
      {
        final String message = "State '" + m_state.getId() + "' does not have the same size " + m_state.getValues().length + " as saved state size " + state.getValues().length;
        Results.putProgression( message );
        throw new RuntimeException( message, e );
      }
      throw new RuntimeException( "Array out of bounds exception when restoring internal state", e );
    }

    if( saveState.isColdStart() )
    {
      // if cold state start, then always start at the startTime of the timeHorizon.
      m_timeStep = 0;
      m_curentTime = m_timeHorizon.getBeginTime().getMJD();
    }
    else
    {
      m_timeStep = saveState.getTimestep();
      m_curentTime = saveState.getTime();
    }
  }

  @Override
  public void releaseInternalState( final IModelState savedInternalState )
  {
    /* nothing to do, let the garbace collector do its work */
  }

  @Override
  public IModelState loadPersistentState( final File persistentStateFile )
  {
    if( !persistentStateFile.exists() )
      throw new RuntimeException( "Could not find file for saved state:" + persistentStateFile.toString() );

    return MapsNoiseModelState.read( persistentStateFile );
  }

  @Override
  public void finish( )
  {
    // nothing needed
  }

  @Override
  public String[] getExchangeItemIDs( )
  {
    return m_items.keySet().toArray( new String[m_items.size()] );
  }

  @Override
  public String[] getExchangeItemIDs( final Role role )
  {
    if( role == Role.Output )
      return getExchangeItemIDs();

    return new String[0];
  }

  @Override
  public IExchangeItem getDataObjectExchangeItem( final String exchangeItemID )
  {
    return null;
  }

  @Override
  public IInstance getParent( )
  {
    return null;
  }

  @Override
  public IVector getParameters( )
  {
    /* we do not have any special parameters */
    // REMARK: explicit exception for now, return empty vector instead?
    throw new UnsupportedOperationException();
  }

  @Override
  public void setParameters( final IVector parameters )
  {
    /* we do not have any special parameters */
  }

  @Override
  public void axpyOnParameters( final double alpha, final IVector vector )
  {
    /* we do not have any special parameters */
    // REMARK: explicit exception for now, noop instead?
    throw new UnsupportedOperationException();
  }

  @Override
  public IStochVector getStateUncertainty( )
  {
    return null;
  }

  @Override
  public IStochVector getParameterUncertainty( )
  {
    return null;
  }

  @Override
  public IStochVector[] getWhiteNoiseUncertainty( final ITime time )
  {
    return new IStochVector[] { m_sysNoiseIntensity };
  }

  @Override
  public boolean isWhiteNoiseStationary( )
  {
    return true;
  }

  @Override
  public ITime[] getWhiteNoiseTimes( final ITime timeSpan )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.getWhiteNoiseTimes(): Not implemented yet." );
  }

  @Override
  public IVector[] getWhiteNoise( final ITime timeSpan )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.getWhiteNoise(): Not implemented yet." );
  }

  @Override
  public void setWhiteNoise( final IVector[] whiteNoise )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.setWhiteNoise(): Not implemented yet." );
  }

  @Override
  public void axpyOnWhiteNoise( final double alpha, final IVector[] vector )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.axpyOnWhiteNoise(): Not implemented yet." );
  }

  @Override
  public void setAutomaticNoiseGeneration( final boolean value )
  {
    m_autoNoise = value;
  }

  @Override
  public IVector getObservedValues( final IObservationDescriptions observationDescriptions )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.getObservedValues(): Not implemented yet." );
  }

  @Override
  public void announceObservedValues( final IObservationDescriptions observationDescriptions )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoisModelInstance.announceObservedValues(): Not implemented yet." );
  }

  @Override
  public IVector getStateScaling( )
  {
    // no real scaling implemented.
    final IVector result = new Vector( getState().getSize() );
    result.setConstant( 1.0 );
    return result;
  }

  @Override
  public IVector[] getStateScaling( final IObservationDescriptions observationDescriptions )
  {
    final int m = observationDescriptions.getObservationCount();
    // no real scaling implemented. TODO add Schur product!
    final IVector result[] = new Vector[m];
    for( int i = 0; i < m; i++ )
    {
      result[i] = new Vector( getState().getSize() );
      result[i].setConstant( 1.0 );
    }
    return result;
  }

  @Override
  public File getModelRunDir( )
  {
    return null;
  }
}