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

import java.util.HashMap;
import java.util.Map;

import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.interfaces.IStochVector;

/**
 * @author Gernot Belger
 */
public class SpatialNoiseSeparableGridGeometry extends AbstractSpatialNoiseGridGeometry
{
  private final Map<Double, SpatialCorrelationCovariance> m_sharedCorrelationCovarianceX = new HashMap<>();

  private final Map<Double, SpatialCorrelationCovariance> m_sharedCorrelationCovarianceY = new HashMap<>();

  public SpatialNoiseSeparableGridGeometry( final double horizontalCorrelationScale, final CoordinatesType coordsType, final ArrayGeometryInfo geometryInfo, final double[] x, final double[] y )
  {
    super( horizontalCorrelationScale, coordsType, geometryInfo, x, y );
  }

  @Override
  public IStochVector createSystemNoise( final double standardWhiteNoise )
  {
    final SpatialCorrelationCovariance xCovarianceMatrix = getSpatialCorrelationCovarianceX( standardWhiteNoise );
    final SpatialCorrelationCovariance yCovarianceMatrix = getSpatialCorrelationCovarianceY( standardWhiteNoise );

    return new Spatial2DCorrelationStochVector( standardWhiteNoise, xCovarianceMatrix, yCovarianceMatrix );
  }

  // REMARK: we share the SpatialCorrelationCovariance between instances (per item-id).
  private SpatialCorrelationCovariance getSpatialCorrelationCovarianceX( final double standardWhiteNoise )
  {
    return m_sharedCorrelationCovarianceX.computeIfAbsent( standardWhiteNoise, stdDev -> createSpatialCorrelationCovariance( stdDev, getX(), createMidCopies( getX(), getY() ) ) );
  }

  private SpatialCorrelationCovariance getSpatialCorrelationCovarianceY( final double standardWhiteNoise )
  {
    return m_sharedCorrelationCovarianceY.computeIfAbsent( standardWhiteNoise, stdDev -> createSpatialCorrelationCovariance( stdDev, createMidCopies( getY(), getX() ), getY() ) );
  }

  private double[] createMidCopies( final double[] x, final double[] y )
  {
    final double midY = 0.5 * (y[0] + y[y.length - 1]);
    final double[] midYCopies = new double[x.length];
    for( int ix = 0; ix < x.length; ix++ )
      midYCopies[ix] = midY;

    return midYCopies;
  }
}