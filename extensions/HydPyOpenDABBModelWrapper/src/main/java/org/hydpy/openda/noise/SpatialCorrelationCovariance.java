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

import org.openda.utils.Matrix;

/**
 * Created by pelgrim on 08-May-17.
 */
public final class SpatialCorrelationCovariance
{
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