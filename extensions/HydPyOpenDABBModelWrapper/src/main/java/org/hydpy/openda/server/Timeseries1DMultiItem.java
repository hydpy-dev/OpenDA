/**
 * Copyright (c) 2021 by
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.joda.time.Instant;
import org.openda.exchange.timeseries.TimeSeries;
import org.openda.interfaces.IArray;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.utils.Array;

/**
 * @author Gernot Belger
 */
final class Timeseries1DMultiItem extends AbstractServerItem<Timeseries1D>
{
  private static final int VALUE_DIMENSION_INDEX = 1;

  private final List<HydPyExchangeItemDescription> m_descriptions;

  private final String m_id;

  public Timeseries1DMultiItem( final String id, final Role role, final String[] itemNames )
  {
    m_id = id;

    final List<HydPyExchangeItemDescription> descriptions = new ArrayList<>( itemNames.length );
    for( final String itemName : itemNames )
    {
      final String itemId = String.format( "%s.%s", id, itemName );
      descriptions.add( new HydPyExchangeItemDescription( itemId, role ) );
    }

    m_descriptions = Collections.unmodifiableList( descriptions );
  }

  @Override
  public String getId( )
  {
    return m_id;
  }

  @Override
  public Collection<HydPyExchangeItemDescription> getExchangeItemDescriptions( )
  {
    return m_descriptions;
  }

  @Override
  public Timeseries1D parseValue( final Instant startTime, final Instant endTime, final long stepSeconds, final String valueText )
  {
    return Timeseries1D.fromHydPy( startTime, endTime, stepSeconds, valueText );
  }

  @Override
  public List<IExchangeItem> toExchangeItems( final Timeseries1D value )
  {
    final List<IExchangeItem> exItems = new ArrayList<>( m_descriptions.size() );

    final double[] times = value.getTimes();
    final IArray allValues = value.getValues();

    for( int column = 0; column < m_descriptions.size(); column++ )
    {
      final HydPyExchangeItemDescription description = m_descriptions.get( column );

      final IArray slice = allValues.getSlice( VALUE_DIMENSION_INDEX, column );
      final double[] values = slice.getValuesAsDoubles();

      // TODO: set role via constructor...
      final String source = "HydPy";
      final String quantity = "unknown";
      final String unit = "unknown";
      final String location = description.getId(); // rather itemname
      final Role role = description.getRole();
      final double x = Double.NaN;
      final double y = Double.NaN;
      final TimeSeries timeSeries = new TimeSeries( times, values, x, y, source, quantity, unit, location, role );
      timeSeries.setId( description.getId() );
      exItems.add( timeSeries );
    }

    return exItems;
  }

  @Override
  public Timeseries1D toValue( final List<IExchangeItem> exItems )
  {
    final TimeSeries firstItem = (TimeSeries)exItems.get( 0 );
    final double[] globalTimes = firstItem.getTimes();
    final double[] globalValues = firstItem.getValuesAsDoubles();

    final int[] dimensions = new int[] { globalTimes.length, exItems.size() };
    final IArray array = new Array( dimensions );

    for( int column = 0; column < exItems.size(); column++ )
    {
      final TimeSeries timeSeries = (TimeSeries)exItems.get( column );

      final double[] times = timeSeries.getTimes();
      final double[] values = timeSeries.getValuesAsDoubles();

      Validate.isTrue( times.length == globalTimes.length );
      Validate.isTrue( values.length == globalValues.length );

      final Array currentArray = new Array( values, new int[] { values.length }, false );
      array.setSlice( currentArray, VALUE_DIMENSION_INDEX, column );
    }

    return new Timeseries1D( globalTimes, array );
  }

  @Override
  public String printValue( final Timeseries1D timeseries )
  {
    return timeseries.printHydPy();
  }

  @Override
  public Timeseries1D mergeToModelRange( final Timeseries1D initialRangeValue, final Timeseries1D currentRangeValue )
  {
    return initialRangeValue.insert( currentRangeValue );
  }

  @Override
  public Timeseries1D restrictToCurrentRange( final Timeseries1D modelRangeValue, final Instant currentStartTime, final Instant currentEndTime )
  {
    return modelRangeValue.restrictToRange( currentStartTime, currentEndTime );
  }
}