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
final class Double0DItem extends AbstractSingleServerItem<Double>
{
  public Double0DItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public Double parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    return Double.parseDouble( valueText );
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final Double value )
  {
    return new DoubleExchangeItem( id, role, value );
  }

  @Override
  protected Double toValue( final IExchangeItem exItem )
  {
    final DoubleExchangeItem dblItem = (DoubleExchangeItem)exItem;
    return dblItem.getValue();
  }

  @Override
  public String printValue( final Double value )
  {
    if( value == null )
    {
      // hm.... hopeyfully this never happens
      return "null";
    }

    if( value.isNaN() )
      return HydPyUtils.HYD_PY_NAN;

    return Double.toString( value );
  }

  @Override
  public Double mergeToModelRange( final Double initialRangeValue, final Double currentRangeValue )
  {
    /* not time dependent */
    return currentRangeValue;
  }

  @Override
  public Double restrictToCurrentRange( final Double modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    /* not time dependent */
    return modelRangeValue;
  }

  @Override
  public Double copy( final Double value )
  {
    /* Double is immutable, can return same reference */
    return value;
  }
}