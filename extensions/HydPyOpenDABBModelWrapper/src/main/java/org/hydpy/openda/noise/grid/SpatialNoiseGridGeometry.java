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
package org.hydpy.openda.noise.grid;

import java.util.HashMap;
import java.util.Map;

import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialCorrelationStochVector;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.interfaces.IStochVector;

/**
 * @author verlaanm
 * @author Gernot Belger
 */
public class SpatialNoiseGridGeometry extends AbstractSpatialNoiseGridGeometry
{
  private final Map<Double, SpatialCorrelationCovariance> m_sharedCorrelationCovariance = new HashMap<>();

  public SpatialNoiseGridGeometry( final double horizontalCorrelationScale, final CoordinatesType coordsType, final ArrayGeometryInfo geometryInfo, final double[] x, final double[] y )
  {
    super( horizontalCorrelationScale, coordsType, geometryInfo, x, y );
  }

  @Override
  public IStochVector createSystemNoise( final double standardWhiteNoise )
  {
    final SpatialCorrelationCovariance covarianceMatrix = getSpatialCorrelationCovariance( standardWhiteNoise );
    return new SpatialCorrelationStochVector( standardWhiteNoise, covarianceMatrix );
  }

  // REMARK: we share the SpatialCorrelationCovariance between instances (per item-id and stadDev).
  private SpatialCorrelationCovariance getSpatialCorrelationCovariance( final double standardWhiteNoise )
  {
    return m_sharedCorrelationCovariance.computeIfAbsent( standardWhiteNoise, this::createSpatialCorrelationCovariance );
  }

  private SpatialCorrelationCovariance createSpatialCorrelationCovariance( final Double standardWhiteNoise )
  {
    final double[] x = getX();
    final double[] y = getY();

    final double x2[] = new double[x.length * y.length];
    final double y2[] = new double[x.length * y.length];
    expandGrid( x, y, x2, y2 );

    return createSpatialCorrelationCovariance( standardWhiteNoise, x2, y2 );
  }

  private static void expandGrid( final double sourceXs[], final double sourceYs[], final double targetXs[], final double targetYs[] )
  {
    int index = 0;
    for( final double x : sourceXs )
    {
      for( final double y : sourceYs )
      {
        targetXs[index] = x;
        targetYs[index] = y;
        index++;
      }
    }
  }
}