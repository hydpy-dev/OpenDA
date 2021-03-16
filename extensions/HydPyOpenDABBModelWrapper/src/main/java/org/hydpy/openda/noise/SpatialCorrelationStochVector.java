/*
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

import org.openda.interfaces.ISqrtCovariance;
import org.openda.interfaces.IStochVector;
import org.openda.interfaces.IVector;
import org.openda.utils.Matrix;
import org.openda.utils.SqrtCovariance;
import org.openda.utils.StochVector;
import org.openda.utils.Vector;

public class SpatialCorrelationStochVector implements IStochVector
{
  // distances are different on a globe
  public enum CoordinatesType
  {
    WGS84,
    XY
  }

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
//    result += "lengthScale=" + m_lengthScale + ",";
    result += "standardDeviation=" + m_standardDeviation;
//    if( x.length < 40 )
//    {
//      result += ",x=" + new Vector( x );
//      result += ",y=" + new Vector( y );
//    }
    result += ")";
    return result;
  }
}