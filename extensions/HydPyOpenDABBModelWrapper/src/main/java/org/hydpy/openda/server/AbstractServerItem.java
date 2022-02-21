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

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
abstract class AbstractServerItem<TYPE> implements IServerItem
{
  private static final String TYPE_DOUBLE_0D = "Double0D";

  private static final String TYPE_DOUBLE_1D = "Double1D";

  private static final String TYPE_TIMESERIES_0D = "TimeSeries0D";

  private static final String TYPE_TIMESERIES_1D = "TimeSeries1D";

  private static final String TYPE_TIME = "TimeItem";

  private static final String TYPE_DURATION = "DurationItem";

  public static AbstractServerItem< ? > fromHydPyType( final String id, final String hydPyType )
  {
    final String split[] = StringUtils.split( hydPyType, "(" );

    // FIXME: we probably need this to avoid excessive server/client communication of simulation timeseries
    final Role role = Role.InOut;

    switch( split[0].trim() )
    {
      case TYPE_DOUBLE_0D:
        return new Double0DItem( id, role );

      case TYPE_DOUBLE_1D:
        return new Double1DItem( id, role );

      case TYPE_TIMESERIES_0D:
        return new Timeseries0DItem( id, role );

      case TYPE_TIMESERIES_1D:
        return new Timeseries1DItem( id, role );

      case TYPE_TIME:
        return new TimeItem( id, role );

      case TYPE_DURATION:
        return new DurationItem( id, role );
    }

    throw new IllegalArgumentException( String.format( "Invalid item type: %s", hydPyType ) );
  }

  public static AbstractServerItem<Instant> newTimeItem( final String id )
  {
    return new TimeItem( id, Role.InOut );
  }

  public static AbstractServerItem<Long> newDurationItem( final String id )
  {
    return new DurationItem( id, Role.InOut );
  }

  private final String m_id;

  private final Role m_role;

  public AbstractServerItem( final String id, final Role role )
  {
    m_id = id;
    m_role = role;
  }

  @Override
  public String getId( )
  {
    return m_id;
  }

  @Override
  public Role getRole( )
  {
    return m_role;
  }

  public abstract TYPE parseValue( Instant startTime, Instant endTime, long stepSeconds, String valueText ) throws HydPyServerException;

  public abstract IExchangeItem toExchangeItem( final TYPE value );

  public abstract TYPE toValue( IExchangeItem exItem );

  public abstract String printValue( TYPE value );

  public abstract TYPE mergeToModelRange( TYPE initialRangeValue, TYPE currentRangeValue );

  public abstract TYPE restrictToCurrentRange( TYPE modelRangeValue, Instant currentStartTime, Instant currentEndTime );
}