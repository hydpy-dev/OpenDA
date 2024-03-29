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

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class DurationItem extends AbstractSingleServerItem<Long>
{
  public DurationItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public Long parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText ) throws HydPyServerException
  {
    return parseDuration( valueText );
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final Long value )
  {
    final double mjd = HydPyUtils.durationToMjd( value );

    return new DoubleExchangeItem( id, role, mjd );
  }

  @Override
  protected Long toValue( final IExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    final double value = dblItem.getValue();

    return HydPyUtils.mjdToDuration( value );
  }

  @Override
  public String printValue( final Long value )
  {
    return printDuration( value );
  }

  private long parseDuration( final String valueText ) throws HydPyServerException
  {
    if( valueText.length() < 2 )
      throw new HydPyServerException( String.format( "Invalid duration: %s", valueText ) );

    final int value = Integer.parseInt( valueText.substring( 0, valueText.length() - 1 ) );
    final char type = valueText.charAt( valueText.length() - 1 );

    switch( type )
    {
      case 's':
        return value;

      case 'm':
        return Duration.standardMinutes( value ).toStandardSeconds().getSeconds();
      case 'h':
        return Duration.standardHours( value ).toStandardSeconds().getSeconds();
      case 'd':
        return Duration.standardDays( value ).toStandardSeconds().getSeconds();

      default:
        throw new HydPyServerException( String.format( "Invalid duration: %s", valueText ) );
    }
  }

  private String printDuration( final double value )
  {
    final Duration seconds = Duration.standardSeconds( (long)value );
    final long standardDays = seconds.getStandardDays();
    if( standardDays > 0 )
      return String.format( "%dd", standardDays );

    final long standardHours = seconds.getStandardHours();
    if( standardHours > 0 )
      return String.format( "%dh", standardHours );

    final long standardMinutes = seconds.getStandardMinutes();
    if( standardMinutes > 0 )
      return String.format( "%dm", standardMinutes );

    return String.format( "%ds", seconds.getStandardSeconds() );
  }

  @Override
  public Long mergeToModelRange( final Long initialRangeValue, final Long currentRangeValue )
  {
    /* not time dependent */
    return currentRangeValue;
  }

  @Override
  public Long restrictToCurrentRange( final Long modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    /* not time dependent */
    return modelRangeValue;
  }

  @Override
  public Long copy( final Long value )
  {
    /* Long is immutable, can return same reference */
    return value;
  }
}