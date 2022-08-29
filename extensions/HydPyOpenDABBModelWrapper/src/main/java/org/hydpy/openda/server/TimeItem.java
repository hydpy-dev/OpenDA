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

import java.text.ParseException;

import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.exchange.timeseries.TimeUtils;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.utils.Time;

/**
 * @author Gernot Belger
 */
final class TimeItem extends AbstractSingleServerItem<Instant>
{
  private static final DateTimeFormatter HYD_PY_DATE_TIME_PARSER = ISODateTimeFormat.dateTimeNoMillis();

  public TimeItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public Instant parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    return Instant.parse( valueText, HYD_PY_DATE_TIME_PARSER );
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final Instant value )
  {
    final Instant date = value;

    final double mjd = Time.milliesToMjd( date.getMillis() );

    try
    {
      // REMARK / IMPORTANT:
      // we use TimeUtils.date2Mjd to produce the final mjd time.
      // This is necessary, because that is the same way, as the time is parsed when
      // applying the restart-time (which is later set to the currentTime of the alogrithm).
      // Why is this necessary: if we use Time.milliesToMjd, a slightly different mjd value
      // will be computed, and this in turn will be a problem in
      // org.openda.algorithms.kalmanFilter.AbstractSequentialAlgorithm#427 where
      // times are compared by their mjd value, hence resulting in a forecast computation
      // between two equal times...
      // REMARK: we do not have this problem with the time-series, as OpenDA always compares
      // times within times series with a small delta

      final String mjdAsString = TimeUtils.mjdToString( mjd );
      final double cleanMjd = TimeUtils.date2Mjd( mjdAsString );

      return new DoubleExchangeItem( id, role, cleanMjd );
    }
    catch( final ParseException e )
    {
      /* should never happen */
      throw new RuntimeException( e );
    }
  }

  @Override
  protected Instant toValue( final IExchangeItem exItem )
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

  @Override
  public Instant mergeToModelRange( final Instant initialRangeValue, final Instant currentRangeValue )
  {
    /* not time dependent */
    return currentRangeValue;
  }

  @Override
  public Instant restrictToCurrentRange( final Instant modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    /* not time dependent */
    return modelRangeValue;
  }

  @Override
  public Instant copy( final Instant value )
  {
    /* Instant is immutable, can return same reference */
    return value;
  }
}