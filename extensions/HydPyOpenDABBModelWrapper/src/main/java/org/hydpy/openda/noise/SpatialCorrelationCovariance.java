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

import org.openda.utils.Matrix;

/**
 * Created by pelgrim on 08-May-17.
 */
public class SpatialCorrelationCovariance
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