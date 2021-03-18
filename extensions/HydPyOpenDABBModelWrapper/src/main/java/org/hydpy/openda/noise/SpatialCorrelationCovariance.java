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

import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.utils.Matrix;

/**
 * Created by pelgrim on 08-May-17.
 *
 * @author Gernot Belger
 */
public final class SpatialCorrelationCovariance
{
  /**
   * Creates a spatial covariance for a list of coordinates based on theirs distances.
   */
  public static SpatialCorrelationCovariance fromCoordinates( final double standardWhiteNoise, final CoordinatesType coordsType, final double[] x, final double[] y, final double horizontalCorrelationScale )
  {
    assert x.length == y.length;

    final int n = x.length;
    final Matrix covariance = new Matrix( n, n );

    final double scale = standardWhiteNoise * standardWhiteNoise;

    final double lengthScaleSquare = horizontalCorrelationScale * horizontalCorrelationScale;

    for( int i = 0; i < n; i++ )
    {
      for( int j = 0; j < n; j++ )
      {
        final double dist = SpatialNoiseUtils.distance( coordsType, x[i], y[i], x[j], y[j] );
        final double covij = scale * Math.exp( -0.5 * dist * dist / lengthScaleSquare );
        covariance.setValue( i, j, covij );
      }
    }

    final double determinantCov = covariance.determinant();
    final Matrix sqrtCovariance = covariance.sqrt();

    return new SpatialCorrelationCovariance( n, sqrtCovariance, determinantCov );
  }

  private final Matrix m_sqrtCovariance;

  private final double m_determinantCov;

  private final int m_size;

  public SpatialCorrelationCovariance( final int size, final Matrix sqrtCovariance, final double determinantCov )
  {
    m_size = size;
    m_sqrtCovariance = sqrtCovariance;
    m_determinantCov = determinantCov;
  }

  public int getSize( )
  {
    return m_size;
  }

  public Matrix getSqrtCovariance( )
  {
    return m_sqrtCovariance;
  }

  public double getDeterminantCov( )
  {
    return m_determinantCov;
  }
}