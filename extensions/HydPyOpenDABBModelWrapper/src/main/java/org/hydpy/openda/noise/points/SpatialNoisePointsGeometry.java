/**
 * Copyright (c) 2021 by
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
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.interfaces.IArrayGeometryInfo;
import org.openda.interfaces.IStochVector;

/**
 * @author Gernot Belger
 */
final class SpatialNoisePointsGeometry implements ISpatialNoiseGeometry
{
  private final Map<Double, SpatialCorrelationCovariance> m_correlationCovariance = new HashMap<>();

  private final CoordinatesType m_coordinatesType;

  private final double m_horizontalCorrelationScale;

  private final double[] m_x;

  private final double[] m_y;

  public SpatialNoisePointsGeometry( final CoordinatesType coordinatesType, final double horizontalCorrelationScale, final double[] x, final double[] y )
  {
    assert x.length == y.length;

    m_coordinatesType = coordinatesType;
    m_horizontalCorrelationScale = horizontalCorrelationScale;

    m_x = x;
    m_y = y;
  }

  @Override
  public int[] getStateDimensions( )
  {
    return new int[] { m_x.length };
  }

  @Override
  public IArrayGeometryInfo getGeometryinfo( )
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

  // REMARK: we share the SpatialCorrelationCovariance between instances.
  private SpatialCorrelationCovariance getSpatialCorrelationCovariance( final double standardWhiteNoise )
  {
    return m_correlationCovariance.computeIfAbsent( standardWhiteNoise, stdDev -> //
    SpatialCorrelationCovariance.fromCoordinates( stdDev, m_coordinatesType, m_x, m_y, m_horizontalCorrelationScale ) );
  }
}