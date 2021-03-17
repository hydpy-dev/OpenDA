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
package org.hydpy.openda.noise.grid;

import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialNoiseUtils;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.interfaces.IArrayGeometryInfo;
import org.openda.utils.Matrix;

/**
 * @author Gernot Belger
 */
public abstract class AbstractSpatialNoiseGridGeometry implements ISpatialNoiseGeometry
{
  private final CoordinatesType m_coordsType;

  private final ArrayGeometryInfo m_geometryInfo;

  private final double[] m_x;

  private final double[] m_y;

  private final double m_horizontalCorrelationScale;

  public AbstractSpatialNoiseGridGeometry( final double horizontalCorrelationScale, final CoordinatesType coordsType, final ArrayGeometryInfo geometryInfo, final double[] x, final double[] y )
  {
    m_horizontalCorrelationScale = horizontalCorrelationScale;
    m_coordsType = coordsType;
    m_geometryInfo = geometryInfo;
    m_x = x;
    m_y = y;
  }

  protected final double[] getX( )
  {
    return m_x;
  }

  protected final double[] getY( )
  {
    return m_y;
  }

  @Override
  public final int[] getStateDimensions( )
  {
    return new int[] { m_x.length, m_y.length };
  }

  @Override
  public final IArrayGeometryInfo getGeometryinfo( )
  {
    return m_geometryInfo;
  }

  protected final SpatialCorrelationCovariance createSpatialCorrelationCovariance( final double standardWhiteNoise, final double[] x, final double[] y )
  {
    final int n = x.length;
    final Matrix covariance = new Matrix( n, n );

    final double scale = standardWhiteNoise * standardWhiteNoise;

    final double lengthScaleSquare = m_horizontalCorrelationScale * m_horizontalCorrelationScale;

    for( int i = 0; i < n; i++ )
    {
      for( int j = 0; j < n; j++ )
      {
        final double dist = SpatialNoiseUtils.distance( m_coordsType, x[i], y[i], x[j], y[j] );
        final double covij = scale * Math.exp( -0.5 * dist * dist / lengthScaleSquare );
        covariance.setValue( i, j, covij );
      }
    }

    final double determinantCov = covariance.determinant();
    final Matrix sqrtCovariance = covariance.sqrt();

    return new SpatialCorrelationCovariance( n, sqrtCovariance, determinantCov );
  }
}