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

import org.hydpy.openda.HydPyUtils;
import org.joda.time.Instant;
import org.openda.exchange.DoublesExchangeItem;
import org.openda.interfaces.IPrevExchangeItem;
import org.openda.interfaces.IPrevExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Double1DItem extends AbstractServerItem
{
  public Double1DItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Object parseValue( final String valueText )
  {
    return HydPyUtils.parseDoubleArray( valueText );
  }

  @Override
  public IPrevExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Object value )
  {
    final double[] values = (double[])value;
    return new DoublesExchangeItem( getId(), getRole(), values );
  }

  @Override
  public String printValue( final IPrevExchangeItem exItem, final long stepSeconds )
  {
    final DoublesExchangeItem dblItem = (DoublesExchangeItem)exItem;
    final double[] doubles = dblItem.getValuesAsDoubles();

    return HydPyUtils.printDoubleArray( doubles );
  }
}