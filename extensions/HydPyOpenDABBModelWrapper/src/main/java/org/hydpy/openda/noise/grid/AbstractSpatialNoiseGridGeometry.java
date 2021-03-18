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

import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.SpatialCorrelationCovariance;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.interfaces.IArrayGeometryInfo;

/**
 * @author verlaanm
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
    return SpatialCorrelationCovariance.fromCoordinates( standardWhiteNoise, m_coordsType, x, y, m_horizontalCorrelationScale );
  }
}