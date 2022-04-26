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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.openda.exchange.ArrayExchangeItem;
import org.openda.exchange.TimeInfo;
import org.openda.interfaces.IDimensionIndex;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.interfaces.IGeometryInfo;
import org.openda.interfaces.IInstance;
import org.openda.interfaces.ILocalizationDomains;
import org.openda.interfaces.IModelState;
import org.openda.interfaces.IObservationDescriptions;
import org.openda.interfaces.IObservationOperator;
import org.openda.interfaces.IResultWriter;
import org.openda.interfaces.IStochModelFactory.OutputLevel;
import org.openda.interfaces.IStochModelInstance;
import org.openda.interfaces.IStochModelInstanceDeprecated;
import org.openda.interfaces.IStochVector;
import org.openda.interfaces.ITime;
import org.openda.interfaces.ITreeVector;
import org.openda.interfaces.IVector;
import org.openda.localization.LocalizationDomainsSimpleModel;
import org.openda.observationOperators.ObservationOperatorDeprecatedModel;
import org.openda.utils.Array;
import org.openda.utils.DimensionIndex;
import org.openda.utils.Instance;
import org.openda.utils.Results;
import org.openda.utils.StochTreeVector;
import org.openda.utils.Time;
import org.openda.utils.TreeVector;
import org.openda.utils.Vector;
import org.openda.utils.geometry.GeometryUtils;

/**
 * Module for generating noise for timeseries of spatially distributed data with (optionally) exponential time-correlation. The spatial correlation is Gaussian.
 * This module is intended for coupling to another model, usually to extend a physical model with some uncertainties.
 * <br/>
 * Reads its configuration from an xml file adhering to the xml-schema specified in spatialNoiseModel.xsd
 * <br/>
 * The actual spatial distribution of the data and its correlation are delegated to implementors of {@link ISpatialNoiseGeometry}s.
 *
 * @author verlaanm
 * @author Gernot Belger
 */
final class SpatialNoiseModelInstance extends Instance implements IStochModelInstance, IStochModelInstanceDeprecated
{
  private final Map<String, SpatialNoiseModelItem> m_items = new HashMap<>();

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

  public SpatialNoiseModelInstance( final int instanceNumber, final OutputLevel outputLevel, final SpatialNoiseModelConfiguration configuration )
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

    final Collection<SpatialNoiseModelConfigurationItem> items = configuration.getItems();
    for( final SpatialNoiseModelConfigurationItem item : items )
    {
      final String id = item.getId();

      final ArrayExchangeItem outputItem = item.createStateExchangeItem();

      final int[] stateDimensions = item.getStateDimensions();

      final int rank = stateDimensions.length;
      final IDimensionIndex[] dimIndex = new IDimensionIndex[rank];
      int numberOfValues = 1;
      for( int i = 0; i < rank; i++ )
      {
        numberOfValues *= stateDimensions[i];
        dimIndex[i] = new DimensionIndex( stateDimensions[i] );
      }
      ArrayUtils.reverse( dimIndex );

      final ITreeVector statePart = new TreeVector( id, new Vector( numberOfValues ), dimIndex );

      final double initialValue = item.getInitialValue();
      statePart.setConstant( initialValue );
      m_state.addChild( statePart );

      final ITreeVector alphaPart = statePart.clone();
      alphaPart.setConstant( item.getAlpha() );
      m_timeCorrelationPerTimeStep.addChild( alphaPart );

      // stochVector for generation of white noise
      final IStochVector systemNoiseChild = item.createSystemNoise();
      m_sysNoiseIntensity.addChild( systemNoiseChild );

      final SpatialNoiseModelItem modelItem = new SpatialNoiseModelItem( id, stateDimensions, outputItem );
      m_items.put( id, modelItem );
    }
  }

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    // REMARK: post-constructor does not make sense, this class only always gets instantiated by its factory by a non-default constructor.
    throw new UnsupportedOperationException();
  }

  @Override
  public IExchangeItem getExchangeItem( final String exchangeItemID )
  {
    final SpatialNoiseModelItem item = m_items.get( exchangeItemID );

    if( item == null )
      return null;

    return item.getOutputSeries();
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
  public IVector getState( final int iDomain )
  {
    return this.getState();
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
  public void axpyOnState( final double alpha, final IVector vector, final int iDomain )
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void compute( final ITime targetTime )
  {
    final double incrementTime = m_timeHorizon.getStepMJD();
    final int nsteps = (int)Math.round( (targetTime.getMJD() - m_curentTime) / incrementTime );
    if( nsteps < 0 )
      throw new RuntimeException( "MapNoisemodel cannot compute backwards in time, ie target<currentTime" );

    final Map<String, Array> allValues = new HashMap<>();

    for( final SpatialNoiseModelItem item : m_items.values() )
    {
      final String id = item.getId();

      final int[] dimensions = item.getStateDimensions();

      final int[] stateDimensions = new int[dimensions.length + 1];
      stateDimensions[0] = nsteps + 1;
      System.arraycopy( dimensions, 0, stateDimensions, 1, dimensions.length );

      final Array state = new Array( stateDimensions );

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
      for( final SpatialNoiseModelItem item : m_items.values() )
      {
        final String id = item.getId();
        final ITreeVector statePart = m_state.getSubTreeVector( id );
        allValues.get( id ).setSlice( statePart.getValues(), 0, nextIndex, nextIndex );
      }
    }

    // output storage set times and values for this compute
    for( final SpatialNoiseModelItem item : m_items.values() )
    {
      final ArrayExchangeItem exItem = item.getOutputSeries();
      exItem.setArray( allValues.get( item.getId() ) );
      exItem.setTimeInfo( new TimeInfo( times ) );
    }
  }

  @Override
  public ILocalizationDomains getLocalizationDomains( )
  {
    return new LocalizationDomainsSimpleModel();
  }

  @Override
  public IVector[] getObservedLocalization( final IObservationDescriptions observationDescriptions, final double distance )
  {
    final IVector xObs = observationDescriptions.getValueProperties( "xposition" );
    final IVector yObs = observationDescriptions.getValueProperties( "yposition" );
    final IVector zObs = observationDescriptions.getValueProperties( "height" );
    final String obsId[] = observationDescriptions.getStringProperties( "id" );
    final int obsCount = observationDescriptions.getObservationCount();

    final IVector[] obsVectorArray = new IVector[obsCount];
    for( int i = 0; i < obsCount; i++ )
    {
      final ITreeVector modelWeightsTreeVector = getLocalizedCohnWeights( obsId[i], distance, xObs.getValue( i ), yObs.getValue( i ), zObs.getValue( i ) );

      final TreeVector weightsForFullNoise = new TreeVector( "Noise-Weight" );
      weightsForFullNoise.addChild( modelWeightsTreeVector );
      obsVectorArray[i] = weightsForFullNoise;
    }

    return obsVectorArray;
  }

  private ITreeVector getLocalizedCohnWeights( final String obsId, final double distanceCohnMeters, final double xObs, final double yObs, final double zObs )
  {
    final TreeVector treeVector = new TreeVector( "weights-for " + obsId );

    for( final SpatialNoiseModelItem item : m_items.values() )
    {
      final IExchangeItem echangeItem = item.getOutputSeries();

      // FIXME: geometry info might be null
      final IGeometryInfo geometryInfo = echangeItem.getGeometryInfo();

      final double[] distancesForExchangeItem = geometryInfo.distanceToPoint( xObs, yObs, zObs ).getValuesAsDoubles();
      final double[] weightsForExchangeItem = new double[distancesForExchangeItem.length];

      for( int xy = 0; xy < distancesForExchangeItem.length; xy++ )
        weightsForExchangeItem[xy] = GeometryUtils.calculateCohnWeight( distancesForExchangeItem[xy], distanceCohnMeters );

      treeVector.addChild( item.getId(), weightsForExchangeItem );
    }

    return treeVector;
  }

  @Override
  public IVector[] getObservedLocalization( final IObservationDescriptions observationDescriptions, final double distance, final int iDomain )
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public IModelState saveInternalState( )
  {
    final boolean coldStart = m_timeStep == -1;
    return new SpatialNoiseModelState( coldStart, m_curentTime, m_timeStep, m_state.clone() );
  }

  @Override
  public void restoreInternalState( final IModelState savedInternalState )
  {
    final SpatialNoiseModelState saveState = (SpatialNoiseModelState)savedInternalState;

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
    /* nothing to do, let the garbage collector do its work */
  }

  @Override
  public IModelState loadPersistentState( final File persistentStateFile )
  {
    if( !persistentStateFile.exists() )
      throw new RuntimeException( "Could not find file for saved state:" + persistentStateFile.toString() );

    return SpatialNoiseModelState.readPersistentState( persistentStateFile );
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

  /**
   * Get the operator that can calculate model values corresponding to a number of observations
   * This returns the operator that calculates what the observations would look like,
   * if reality would be equal to the current stoch model state.
   *
   * @return Observation operator
   */
  @Override
  public IObservationOperator getObservationOperator( )
  {
    return new ObservationOperatorDeprecatedModel( this );
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