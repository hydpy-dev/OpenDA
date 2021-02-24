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
    Instant date = (Instant)value;

    if( IHydPyInstance.ITEM_ID_FIRST_DATE.equals( getId() ) && IHydPyInstance.ITEM_ID_LAST_DATE.equals( getId() ) )
    {
      // REMARK: HydPy thinks in time-interval, hence we have one timestep less --> start with startTime + stepSeconds
      date = date.plus( stepSeconds * 1000 );
    }

    final double mjd = HydPyUtils.dateToMjd( date );

    return new DoubleExchangeItem( getId(), getRole(), mjd );
  }

  @Override
  public String printValue( final IPrevExchangeItem exItem, final long stepSeconds )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    final double value = dblItem.getValue();
    Instant date = HydPyUtils.mjdToDate( value );

    if( IHydPyInstance.ITEM_ID_FIRST_DATE.equals( getId() ) && IHydPyInstance.ITEM_ID_LAST_DATE.equals( getId() ) )
    {
      // REMARK: HydPy thinks in time-intervals, hence we have one timestep less --> start with startTime + stepSeconds
      date = date.minus( stepSeconds * 1000 );
    }

    return HYD_PY_DATE_TIME_PARSER.print( date );
  }
}