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

import org.openda.blackbox.interfaces.ITimeHorizonConsumer;
import org.openda.interfaces.IStochModelFactory;
import org.openda.interfaces.IStochModelInstance;
import org.openda.interfaces.IStochModelPostProcessor;
import org.openda.interfaces.ITime;

/**
 * FIXME: comment...
 * This class was derived from the original {@link MapsNoiseModelFactory} of OpenDA Core.
 * <br/>
 * FIXME: comment...
 * Module for modeling spatially and temporally correlated noise in 2d.
 * A gaussian spatial correlation is assumed and exponential temporal correlation.
 * The probablities are all Gaussian.
 *
 * @author verlaanm
 * @author belger
 */
public final class SpatialNoiseModelFactory implements IStochModelFactory, ITimeHorizonConsumer
{
  // Counter for keeping track of instances
  private static int NEXT_INSTANCE_NUMBER = 1;

  private static SpatialNoiseModelConfiguration m_configuration;

  private File m_workingDir = null;

  private String[] m_arguments = null;

  private ITime m_timeHorizon = null;

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    m_workingDir = workingDir;
    m_arguments = arguments;
  }

  @Override
  public void setTimeHorizon( final ITime timeHorizon )
  {
    m_timeHorizon = timeHorizon;
  }

  @Override
  public IStochModelInstance getInstance( final OutputLevel outputLevel )
  {
    final SpatialNoiseModelConfiguration configuration = readConfiguration();

    // Instance counter
    final int instanceNumber = NEXT_INSTANCE_NUMBER;
    NEXT_INSTANCE_NUMBER++;

    return new SpatialNoiseModelInstance( instanceNumber, outputLevel, configuration );
  }

  /**
   * REMARK: we read the configuration once here for reasons
   * a) performance
   * b) the configuration items reuse the correlation covariance for all model instances.
   */
  private synchronized SpatialNoiseModelConfiguration readConfiguration( )
  {
    if( m_configuration == null )
      m_configuration = SpatialNoiseModelConfiguration.read( m_workingDir, m_arguments, m_timeHorizon );

    return m_configuration;
  }

  @Override
  public IStochModelPostProcessor getPostprocessorInstance( final File instanceDir )
  {
    throw new UnsupportedOperationException( "org.openda.noiseModels.MapsNoiseModelFactory.getPostprocessorInstance(): Not implemented yet." );
  }

  @Override
  public void finish( )
  {
    // no action needed (yet)
  }

  @Override
  public String toString( )
  {
    final StringBuilder buffer = new StringBuilder( this.getClass().getName() );
    buffer.append( " {" );

    buffer.append( "\n\t[" );
    buffer.append( String.join( ", ", m_arguments ) );
    buffer.append( "\n\t]" );

    if( m_workingDir != null )
    {
      buffer.append( "\n\t" );
      buffer.append( m_workingDir.getAbsolutePath() );
    }

    buffer.append( "}" );
    return buffer.toString();
  }
}