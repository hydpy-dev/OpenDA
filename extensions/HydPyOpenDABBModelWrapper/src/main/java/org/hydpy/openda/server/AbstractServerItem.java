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

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
abstract class AbstractServerItem<TYPE>
{
  private static final String TYPE_DOUBLE_0D = "Double0D";

  private static final String TYPE_DOUBLE_1D = "Double1D";

  private static final String TYPE_TIMESERIES_0D = "TimeSeries0D";

  private static final String TYPE_TIMESERIES_1D = "TimeSeries1D";

  private static final String TYPE_TIME = "TimeItem";

  private static final String TYPE_DURATION = "DurationItem";

  private final boolean m_isInitialStateShared;

  public AbstractServerItem( final boolean isInitialStateShared )
  {
    m_isInitialStateShared = isInitialStateShared;
  }

  public boolean isInitialStateShared( )
  {
    return m_isInitialStateShared;
  }

  public static AbstractServerItem< ? > fromHydPyType( final String id, final String hydPyType, final String[] itemNames )
  {
    final String split[] = StringUtils.split( hydPyType, "(" );

    // FIXME: we probably need this to avoid excessive server/client communication of simulation timeseries
    final Role role = determineRole( id );
    // FIXME: hacky for now; determine via spezialized item id
    final boolean isInitialStateShared = id.contains( ".shared" );

    switch( split[0].trim() )
    {
      case TYPE_DOUBLE_0D:
        return new Double0DItem( id, role, isInitialStateShared );

      case TYPE_DOUBLE_1D:
        return new Double1DItem( id, role, isInitialStateShared );

      case TYPE_TIMESERIES_0D:
        return new Timeseries0DItem( id, role, isInitialStateShared );

      case TYPE_TIMESERIES_1D:
      {
        // TODO: HACK: special handling of some items that we want to split into multiple exchange items
        if( id.contains( ".split" ) )
          return new Timeseries1DMultiItem( id, role, isInitialStateShared, itemNames );

        return new Timeseries1DItem( id, role, isInitialStateShared );
      }

      case TYPE_TIME:
        return new TimeItem( id, role, isInitialStateShared );

      case TYPE_DURATION:
        return new DurationItem( id, role, isInitialStateShared );
    }

    throw new IllegalArgumentException( String.format( "Invalid item type: %s", hydPyType ) );
  }

  private static Role determineRole( final String id )
  {
    final String[] split = StringUtils.split( id, '.' );
    for( final String token : split )
    {
      if( token.startsWith( "role_" ) )
      {
        final String role = token.substring( "role_".length() );
        return Role.valueOf( role );
      }
    }

    /* default value */
    return Role.InOut;
  }

  public static AbstractServerItem<Instant> newTimeItem( final String id )
  {
    return new TimeItem( id, Role.InOut, false );
  }

  public static AbstractServerItem<Long> newDurationItem( final String id )
  {
    return new DurationItem( id, Role.InOut, false );
  }

  public abstract String getId( );

  public abstract Collection<HydPyExchangeItemDescription> getExchangeItemDescriptions( );

  public abstract TYPE parseValue( Instant startTime, Instant endTime, long stepSeconds, String valueText ) throws HydPyServerException;

  public abstract List<IExchangeItem> toExchangeItems( final TYPE value );

  public abstract TYPE toValue( List<IExchangeItem> exItems );

  public abstract String printValue( TYPE value );

  public abstract TYPE mergeToModelRange( TYPE initialRangeValue, TYPE currentRangeValue );

  public abstract TYPE restrictToCurrentRange( TYPE modelRangeValue, Instant currentStartTime, Instant currentEndTime );

  public abstract TYPE copy( TYPE value );
}