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
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IPrevExchangeItem;
import org.openda.interfaces.IPrevExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class TimeItem extends AbstractServerItem
{
  private static final DateTimeFormatter HYD_PY_DATE_TIME_PARSER = ISODateTimeFormat.dateTimeNoMillis();

  public TimeItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Object parseValue( final String valueText )
  {
    return Instant.parse( valueText, HYD_PY_DATE_TIME_PARSER );
  }

  @Override
  public IPrevExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Object value )
  {
    final Instant date = (Instant)value;

    final double mjd = HydPyUtils.dateToMjd( date );

    return new DoubleExchangeItem( getId(), getRole(), mjd );
  }

  @Override
  public String printValue( final IPrevExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    final double value = dblItem.getValue();
    final Instant date = HydPyUtils.mjdToDate( value );

    return HYD_PY_DATE_TIME_PARSER.print( date );
  }
}