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
import org.openda.interfaces.IArray;

/**
 * @author Gernot Belger
 */
final class Timeseries1D
{
  private final double[] m_times;

  private final IArray m_values;

  public Timeseries1D( final double[] times, final IArray values )
  {
    final int[] dimensions = values.getDimensions();
    Validate.isTrue( dimensions.length == 2 );
    Validate.isTrue( dimensions[0] == times.length );

    m_times = times;
    m_values = values;
  }

  public double[] getTimes( )
  {
    return m_times;
  }

  public IArray getValues( )
  {
    return m_values;
  }
}