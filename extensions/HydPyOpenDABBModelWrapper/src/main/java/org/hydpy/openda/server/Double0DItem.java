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
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Double0DItem extends AbstractServerItem
{
  public Double0DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Object parseValue( final String valueText )
  {
    return Double.parseDouble( valueText );
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Object value )
  {
    return new DoubleExchangeItem( getId(), getRole(), (Double)value );
  }

  @Override
  public String printValue( final IExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    final double value = dblItem.getValue();
    return Double.toString( value );
  }
}