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

import org.openda.interfaces.ISqrtCovariance;
import org.openda.interfaces.IStochVector;
import org.openda.interfaces.IVector;
import org.openda.utils.Matrix;
import org.openda.utils.SqrtCovariance;
import org.openda.utils.StochVector;
import org.openda.utils.Vector;

/**
 * @author verlaanm
 * @author Gernot Belger
 */
public final class SpatialCorrelationStochVector implements IStochVector
{
  private final double m_standardDeviation;

  private final Matrix m_sqrtCovariance;

  private final double m_determinantCov;

  private final Vector m_mean;

  private final Vector m_standardDeviations;

  private final StochVector m_whiteNoise;

  public SpatialCorrelationStochVector( final double standardDeviation, final SpatialCorrelationCovariance correlationCovariance )
  {
    m_standardDeviation = standardDeviation;

    final int n = correlationCovariance.getSize();
    m_mean = new Vector( n );

    m_standardDeviations = new Vector( n );
    m_standardDeviations.setConstant( m_standardDeviation );

    m_determinantCov = correlationCovariance.getDeterminantCov();
    m_sqrtCovariance = correlationCovariance.getSqrtCovariance();

    final IVector zeroMean = new Vector( n );
    final IVector stdOne = new Vector( n );
    stdOne.setConstant( 1.0 );
    m_whiteNoise = new StochVector( zeroMean, stdOne );
  }

  @Override
  public IVector createRealization( )
  {
    final IVector result = m_mean.clone();
    final IVector whiteSample = m_whiteNoise.createRealization();
    m_sqrtCovariance.rightMultiply( 1.0, whiteSample, 1.0, result ); // x=alpha*x+beta*L*v
    return result;
  }

  @Override
  public double evaluatePdf( final IVector tv )
  {
    final IVector diff = tv.clone();
    diff.axpy( -1.0, m_mean ); // this=alpha*x+this

    final IVector decorrelated = m_whiteNoise.getExpectations(); // decorrelated=0 of right size
    m_sqrtCovariance.rightSolve( diff, decorrelated );
    return m_whiteNoise.evaluatePdf( decorrelated ) / Math.sqrt( m_determinantCov );
  }

  @Override
  public IVector getExpectations( )
  {
    return m_mean.clone();
  }

  @Override
  public ISqrtCovariance getSqrtCovariance( )
  {
    return new SqrtCovariance( m_sqrtCovariance );
  }

  @Override
  public boolean hasCorrelatedElements( )
  {
    return true;
  }

  @Override
  public IVector getStandardDeviations( )
  {
    return m_standardDeviations.clone();
  }

  @Override
  public String toString( )
  {
    String result = "SpatialCorrelationStochVector(";
    result += "standardDeviation=" + m_standardDeviation;
    result += ")";
    return result;
  }
}