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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.openda.interfaces.IModelState;
import org.openda.interfaces.IVector;
import org.openda.utils.Results;
import org.openda.utils.Vector;

/**
 * 'State' of the {@link MapsNoiseModelInstance} used for saving/restoring its state from a file.
 *
 * @author verlaanm
 * @author Gernot Belger
 */
final class SpatialNoiseModelState implements IModelState
{
  private static final String PROPERTY_STATE = "state"; //$NON-NLS-1$

  private static final String PROPERTY_COLDSTART = "coldstart"; //$NON-NLS-1$

  private static final String PROPERTY_TIME = "time"; //$NON-NLS-1$

  private static final String PROPERTY_TIMESTEP = "timestep"; //$NON-NLS-1$

  public static IModelState readPersistentState( final File persistentStateFile )
  {
    final Properties properties = new Properties();

    try( final FileInputStream in = new FileInputStream( persistentStateFile ) )
    {
      properties.load( in );
    }
    catch( final IOException e )
    {
      Results.putMessage( "Exception: " + e.getMessage() );
      throw new RuntimeException( "Error reading restart file for model: " + persistentStateFile.getAbsolutePath() );
    }

    // FIXME: we should do more validation if the properties are correctly set
    final Vector x = new Vector( properties.getProperty( PROPERTY_STATE ) );

    final boolean coldStart = Boolean.parseBoolean( properties.getProperty( PROPERTY_COLDSTART ) );
    final double t = Double.parseDouble( properties.getProperty( PROPERTY_TIME ) );
    final int timestep = Integer.parseInt( properties.getProperty( PROPERTY_TIMESTEP ) );

    return new SpatialNoiseModelState( coldStart, t, timestep, x );
  }

  private final boolean m_coldStart;

  private final double m_time;

  private final int m_timestep;

  private final IVector m_state;

  public SpatialNoiseModelState( final boolean coldStart, final double time, final int timestep, final IVector state )
  {
    m_coldStart = coldStart;
    m_time = time;
    m_timestep = timestep;
    m_state = state;
  }

  public boolean isColdStart( )
  {
    return m_coldStart;
  }

  public double getTime( )
  {
    return m_time;
  }

  public int getTimestep( )
  {
    return m_timestep;
  }

  public IVector getState( )
  {
    return m_state;
  }

  @Override
  public void savePersistentState( final File file )
  {
    final Properties properties = new Properties();

    // save state vector
    final Vector tempVec = new Vector( m_state );
    final int n = m_state.getSize() + 1;

    properties.setProperty( PROPERTY_STATE, tempVec.toString( n ) );
    properties.setProperty( PROPERTY_COLDSTART, Boolean.toString( m_coldStart ) );
    properties.setProperty( PROPERTY_TIME, Double.toString( m_time ) );
    properties.setProperty( PROPERTY_TIMESTEP, Integer.toString( m_timestep ) );

    try( final FileOutputStream out = new FileOutputStream( file ) )
    {
      properties.store( out, PROPERTY_COLDSTART );
    }
    catch( final IOException e )
    {
      throw new RuntimeException( "Could not create state file " + file.getAbsolutePath(), e );
    }
  }
}