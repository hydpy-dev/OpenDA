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
package org.hydpy.openda.noise.points;

import java.util.HashMap;
import java.util.Map;

import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialCorrelationStochVector;
import org.hydpy.openda.noise.SpatialNoiseUtils;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.interfaces.IArrayGeometryInfo;
import org.openda.interfaces.IStochVector;
import org.openda.utils.Matrix;

/**
 * @author Gernot Belger
 */
public class SpatialNoisePointsGeometry implements ISpatialNoiseGeometry
{
  private final CoordinatesType m_coordinatesType;

  private final double m_horizontalCorrelationScale;

  private final double[] m_x;

  private final double[] m_y;

  private final Map<Double, SpatialCorrelationCovariance> m_sharedCorrelationCovariance = new HashMap<>();

  public SpatialNoisePointsGeometry( final CoordinatesType coordinatesType, final double horizontalCorrelationScale, final double[] x, final double[] y )
  {
    assert x.length == y.length;

    m_coordinatesType = coordinatesType;
    m_horizontalCorrelationScale = horizontalCorrelationScale;
    m_x = x;
    m_y = y;
  }

  @Override
  public final int[] getStateDimensions( )
  {
    return new int[] { m_x.length };
  }

  @Override
  public final IArrayGeometryInfo getGeometryinfo( )
  {
    // REMARK: set to null, because a) it only supports grid-like geometries b) its only used to transform between spatial different model- and noise-exchange item
    // However for the irregular points, we assume that model- and spatial- items match exactly.
    return null;
  }

  @Override
  public IStochVector createSystemNoise( final double standardWhiteNoise )
  {
    final SpatialCorrelationCovariance covarianceMatrix = getSpatialCorrelationCovariance( standardWhiteNoise );
    return new SpatialCorrelationStochVector( standardWhiteNoise, covarianceMatrix );
  }

  // REMARK: we share the SpatialCorrelationCovariance between instances (per item-id).
  private SpatialCorrelationCovariance getSpatialCorrelationCovariance( final double standardWhiteNoise )
  {
    return m_sharedCorrelationCovariance.computeIfAbsent( standardWhiteNoise, this::createSpatialCorrelationCovariance );
  }

  private final SpatialCorrelationCovariance createSpatialCorrelationCovariance( final double standardWhiteNoise )
  {
    final int n = m_x.length;
    final Matrix covariance = new Matrix( n, n );

    final double scale = standardWhiteNoise * standardWhiteNoise;

    final double lengthScaleSquare = m_horizontalCorrelationScale * m_horizontalCorrelationScale;

    for( int i = 0; i < n; i++ )
    {
      for( int j = 0; j < n; j++ )
      {
        final double dist = SpatialNoiseUtils.distance( m_coordinatesType, m_x[i], m_y[i], m_x[j], m_y[j] );
        final double covij = scale * Math.exp( -0.5 * dist * dist / lengthScaleSquare );
        covariance.setValue( i, j, covij );
      }
    }

    final double determinantCov = covariance.determinant();
    final Matrix sqrtCovariance = covariance.sqrt();

    return new SpatialCorrelationCovariance( n, sqrtCovariance, determinantCov );
  }
}