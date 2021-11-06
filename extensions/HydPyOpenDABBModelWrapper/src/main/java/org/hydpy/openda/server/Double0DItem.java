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
final class Double0DItem extends AbstractServerItem<Double>
{
  public Double0DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Double parseValue( final String valueText )
  {
    return Double.parseDouble( valueText );
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Double value )
  {
    return new DoubleExchangeItem( getId(), getRole(), value );
  }

  @Override
  public Double toValue( final Instant startTime, final Instant endTime, final long stepSeconds, final IExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    return dblItem.getValue();
  }

  @Override
  public String printValue( final Double value )
  {
    return Double.toString( value );
  }
}