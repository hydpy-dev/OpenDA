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
package org.hydpy.openda.server;

import org.joda.time.Instant;
import org.openda.exchange.DoublesExchangeItem;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Double1DItem extends AbstractServerItem<double[]>
{
  public Double1DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public double[] parseValue( final String valueText )
  {
    return HydPyUtils.parseDoubleArray( valueText );
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final double[] value )
  {
    return new DoublesExchangeItem( getId(), getRole(), value );
  }

  @Override
  public double[] toValue( final Instant startTime, final Instant endTime, final long stepSeconds, final IExchangeItem exItem )
  {
    final DoublesExchangeItem dblItem = (DoublesExchangeItem)exItem;
    return dblItem.getValuesAsDoubles();
  }

  @Override
  public String printValue( final double[] value )
  {
    return HydPyUtils.printDoubleArray( value );
  }
}