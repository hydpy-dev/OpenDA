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
package org.hydpy.openda.server;

import org.apache.commons.lang3.Validate;

final class Timeseries0D
{
  private final double[] m_times;

  private final double[] m_values;

  public Timeseries0D( final double[] times, final double[] values )
  {
    Validate.isTrue( times.length == values.length );

    m_times = times;
    m_values = values;
  }

  public double[] getTimes( )
  {
    return m_times;
  }

  public double[] getValues( )
  {
    return m_values;
  }
}