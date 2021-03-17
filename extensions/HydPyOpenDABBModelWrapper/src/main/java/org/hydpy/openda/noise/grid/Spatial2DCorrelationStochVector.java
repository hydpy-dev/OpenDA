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
package org.hydpy.openda.noise.grid;

import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialCorrelationStochVector;
import org.openda.interfaces.IMatrix;
import org.openda.interfaces.ISqrtCovariance;
import org.openda.interfaces.IStochVector;
import org.openda.interfaces.IVector;
import org.openda.utils.Matrix;
import org.openda.utils.StochVector;
import org.openda.utils.Vector;

/**
 * This StochVector is a faster implementation for the SpatialCorrelationStochVector for regular 2D-grids.
 * It uses separability of the directions to speed up the computations.
 *
 * @author verlaanm
 */
final class Spatial2DCorrelationStochVector implements IStochVector
{
  private final int m_xn;

  private final int m_yn;

  private final double m_standardDeviation;

  private final IMatrix m_xSqrtCov;

  private final IMatrix m_ySqrtCov;

  private final StochVector m_whiteNoise;

  /**
   * Constructor for a StochVector with Gaussian correlations in space on a regular grid. Both directions
   * are treated as independent (separable) ie r(x1,y1,x2,y2)=r_x(x1,x2)*r_y(y1,y2)
   *
   * @param coordsType
   *          compute distances on a plane in xy or an approximation to part of a sphere. Note that the
   *          computed distances are not truly computed on a sphere, due to the separation of lat and lon directions.
   * @param standardDeviation
   * @param lengthscale
   *          spatial correlation length-scale
   * @param x
   *          grid in x-direction
   * @param y
   *          grid in y-direction
   */
  public Spatial2DCorrelationStochVector( final double standardDeviation, final SpatialCorrelationCovariance xCorrelationCovariance, final SpatialCorrelationCovariance yCorrelationCovariance )
  {
    m_standardDeviation = standardDeviation;

    m_xn = xCorrelationCovariance.getSize();
    m_yn = yCorrelationCovariance.getSize();

    final SpatialCorrelationStochVector xStochVector = new SpatialCorrelationStochVector( 1.0, xCorrelationCovariance );
    m_xSqrtCov = xStochVector.getSqrtCovariance().asMatrix();

    final SpatialCorrelationStochVector yStochVector = new SpatialCorrelationStochVector( 1.0, yCorrelationCovariance );
    m_ySqrtCov = yStochVector.getSqrtCovariance().asMatrix();

    m_whiteNoise = new StochVector( m_xn * m_yn, 0., 1. );
  }

  @Override
  public IVector createRealization( )
  {
    final Vector whiteSample = m_whiteNoise.createRealization();
    final Matrix sampleMatrix = new Matrix( whiteSample, m_yn, m_xn );
    final Matrix xCorrelatedSample = new Matrix( m_yn, m_xn );
    xCorrelatedSample.multiply( 1.0, sampleMatrix, m_xSqrtCov, 0., false, true );
    final Matrix xyCorrelatedSample = new Matrix( m_yn, m_xn );
    xyCorrelatedSample.multiply( m_standardDeviation, m_ySqrtCov, xCorrelatedSample, 0., false, false );
    return xyCorrelatedSample.asVector();
  }

  @Override
  public double evaluatePdf( final IVector tv )
  {
    throw new UnsupportedOperationException( "No evaluatePdf for Spatial2DCorrelationStochVector" );
  }

  @Override
  public IVector getExpectations( )
  {
    return new Vector( m_xn * m_yn );
  }

  @Override
  public ISqrtCovariance getSqrtCovariance( )
  {
    throw new UnsupportedOperationException( "No getSsqrtCovariance for Spatial2DCorrelationStochVector" );
  }

  @Override
  public boolean hasCorrelatedElements( )
  {
    return true;
  }

  @Override
  public IVector getStandardDeviations( )
  {
    final Vector result = new Vector( m_xn * m_yn );
    result.setConstant( m_standardDeviation );
    return result;
  }

  @Override
  public String toString( )
  {
    String result = "Spatial2DCorrelationStochVector(";
//    result += "lengthScale=" + m_lengthScale + ",";
    result += "standardDeviation=" + m_standardDeviation;
//    if( x.length < 40 )
//    {
//      result += ",x=" + new Vector( x );
//      result += ",y=" + new Vector( y );
//    }
//    else
//    {
//      result += ",x=[" + x[0] + "," + x[1] + ", ... ," + x[x.length - 1] + "]";
//      result += ",y=[" + y[0] + "," + y[1] + ", ... ," + y[y.length - 1] + "]";
//    }
    result += ")";
    return result;
  }
}
