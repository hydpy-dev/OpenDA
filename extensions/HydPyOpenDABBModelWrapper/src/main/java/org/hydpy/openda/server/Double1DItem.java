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

import java.util.Arrays;

import org.joda.time.Instant;
import org.openda.exchange.DoublesExchangeItem;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
final class Double1DItem extends AbstractSingleServerItem<double[]>
{
  public Double1DItem( final String id, final Role role, final boolean isInitialStateShared )
  {
    super( id, role, isInitialStateShared );
  }

  @Override
  public double[] parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    return HydPyUtils.parseDoubleArray( valueText );
  }

  @Override
  protected IExchangeItem toExchangeItem( final String id, final Role role, final double[] value )
  {
    return new DoublesExchangeItem( id, role, value );
  }

  @Override
  protected double[] toValue( final IExchangeItem exItem )
  {
    final DoublesExchangeItem dblItem = (DoublesExchangeItem)exItem;
    return dblItem.getValuesAsDoubles();
  }

  @Override
  public String printValue( final double[] value )
  {
    return HydPyUtils.printDoubleArray( value );
  }

  @Override
  public double[] mergeToModelRange( final double[] initialRangeValue, final double[] currentRangeValue )
  {
    /* not time dependent */
    return currentRangeValue;
  }

  @Override
  public double[] restrictToCurrentRange( final double[] modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    /* not time dependent */
    return modelRangeValue;
  }

  @Override
  public double[] copy( final double[] value )
  {
    return Arrays.copyOf( value, value.length );
  }
}