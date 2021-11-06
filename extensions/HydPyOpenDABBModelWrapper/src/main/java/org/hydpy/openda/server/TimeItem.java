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
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.utils.Time;

/**
 * @author Gernot Belger
 */
final class TimeItem extends AbstractServerItem<Instant>
{
  private static final DateTimeFormatter HYD_PY_DATE_TIME_PARSER = ISODateTimeFormat.dateTimeNoMillis();

  public TimeItem( final String id, final Role role )
  {
    super( id, role );
  }

  @Override
  public Instant parseValue( final String valueText )
  {
    return Instant.parse( valueText, HYD_PY_DATE_TIME_PARSER );
  }

  @Override
  public IExchangeItem toExchangeItem( final Instant startTime, final Instant endTime, final long stepSeconds, final Instant value )
  {
    final Instant date = value;

    final double mjd = Time.milliesToMjd( date.getMillis() );

    return new DoubleExchangeItem( getId(), getRole(), mjd );
  }

  @Override
  public Instant toValue( final Instant startTime, final Instant endTime, final long stepSeconds, final IExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    final double value = dblItem.getValue();

    final long timeInMillis = Time.mjdToMillies( value );
    return new Instant( timeInMillis );
  }

  @Override
  public String printValue( final Instant value )
  {
    return HYD_PY_DATE_TIME_PARSER.print( value );
  }
}