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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.joda.time.Instant;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IPrevExchangeItem;

/**
 * Handles the OpenDA specific way on how to call a HydPy-Server.
 *
 * @author Gernot Belger
 */
final class HydPyServerOpenDA implements IHydPyServerProcess
{
  // REMARK: The life-cycle of the OpenDA model-instances enforce a very specific way, on
  // how the model must be called and specifically on how the internal model state must be
  // preserved and restored.

  private static final String METHODS_GET_ITEMTYPES__OPENDA = //
      "GET_query_itemtypes"; //

  private static final String METHODS_INITIALIZE_ITEMTYPES__OPENDA = //
      "GET_register_initialitemvalues"; // writes the initial state into the instance-registry

  private static final String METHODS_GET_SIMULATE__OPENDA = //
      "GET_activate_simulationdates," + //
          "GET_activate_parameteritemvalues," + //
          "GET_load_internalconditions," + //
          "GET_activate_conditionitemvalues," + //
          "GET_simulate," + //
          "GET_save_internalconditions," + //
          "GET_update_conditionitemvalues," + //
          "GET_update_getitemvalues";

  private static final String METHODS_GET_INITIALISATIONTIMEGRID_OPENDA = //
      "GET_query_initialisationtimegrid"; //

  private static final String METHODS_SET_SIMULATIONDATES_OPENDA = //
      "POST_register_simulationdates"; //

  private static final String METHODS_GET_ITEMVALUES_OPENDA = //
      "GET_query_conditionitemvalues," + //
          "GET_query_getitemvalues," + //
          "GET_query_parameteritemvalues," + //
          "GET_query_simulationdates"; //

  private static final String METHODS_GET_FIXED_ITEMVALUES_OPENDA = //
      "GET_query_conditionitemvalues," + //
          "GET_query_getitemvalues," + //
          "GET_query_parameteritemvalues"; //

  private static final String METHODS_POST_ITEMVALUES__OPENDA = //
      "POST_register_simulationdates," + //
          "POST_register_parameteritemvalues," + //
          "POST_register_conditionitemvalues";

  private static final String ITEM_ID_FIRST_DATE_INIT = "firstdate_init"; //$NON-NLS-1$

  private static final String ITEM_ID_LAST_DATE_INIT = "lastdate_init"; //$NON-NLS-1$

  private static final String ITEM_ID_STEP_SIZE = "stepsize"; //$NON-NLS-1$

  private List<IServerItem> m_items = null;

  private Map<String, AbstractServerItem> m_itemIndex = null;

  private final Map<String, Object> m_fixedItems;

  private final Collection<String> m_fixedParameters;

  private final long m_stepSeconds;

  private final String m_firstDateValue;

  private final String m_lastDateValue;

  private final HydPyServerProcess m_server;

  public HydPyServerOpenDA( final HydPyServerProcess server, final Collection<String> fixedParameters ) throws HydPyServerException
  {
    m_server = server;
    m_fixedParameters = fixedParameters;

    final List<IServerItem> items = requestItems();
    m_items = Collections.unmodifiableList( items );

    m_itemIndex = new HashMap<>( items.size() );
    for( final IServerItem item : items )
      m_itemIndex.put( item.getId(), (AbstractServerItem)item );

    m_fixedItems = requestFixedItems();

    /* retreive init-dates and stepsize */
    final Properties props = m_server.execute( null, METHODS_GET_INITIALISATIONTIMEGRID_OPENDA );

    m_firstDateValue = props.getProperty( ITEM_ID_FIRST_DATE_INIT );
    m_lastDateValue = props.getProperty( ITEM_ID_LAST_DATE_INIT );

    /* detemrine step seconds */
    final String stepValue = props.getProperty( ITEM_ID_STEP_SIZE );
    final AbstractServerItem stepServerItem = getItem( ITEM_ID_STEP_SIZE );
    final Object stepParsedValue = stepServerItem.parseValue( stepValue );
    final IPrevExchangeItem stepItem = stepServerItem.toExchangeItem( null, null, 0, stepParsedValue );
    m_stepSeconds = (long)((DoubleExchangeItem)stepItem).getValue();

    // REMARK: set 'stepSeconds' as fixed item, as these are not return from HydPy from any state
    m_fixedItems.put( ITEM_ID_STEP_SIZE, m_stepSeconds );
  }

  /**
   * For debug purposes only
   */
  private void log( final String message, final Object... arguments )
  {
    final String msg = String.format( message, arguments );

    // TODO: use logging framework or something
    System.out.print( m_server.getName() );
    System.out.print( ": " ); //$NON-NLS-1$
    System.out.println( msg );
  }

  private AbstractServerItem getItem( final String id )
  {
    if( m_itemIndex == null )
      throw new IllegalStateException();

    final AbstractServerItem item = m_itemIndex.get( id );

    if( item == null )
      throw new IllegalArgumentException( String.format( "Invalid item id: %s", id ) );

    return item;
  }

  @Override
  public List<IServerItem> getItems( )
  {
    return m_items;
  }

  private List<IServerItem> requestItems( ) throws HydPyServerException
  {
    final Properties props = m_server.execute( null, METHODS_GET_ITEMTYPES__OPENDA );

    final List<IServerItem> items = new ArrayList<>( props.size() );

    for( final String property : props.stringPropertyNames() )
    {
      final String value = props.getProperty( property );

      final AbstractServerItem item = AbstractServerItem.fromHydPyType( property, value );
      items.add( item );
    }

    items.add( AbstractServerItem.newTimeItem( IHydPyInstance.ITEM_ID_FIRST_DATE ) );
    items.add( AbstractServerItem.newTimeItem( IHydPyInstance.ITEM_ID_LAST_DATE ) );
    items.add( AbstractServerItem.newDurationItem( IHydPyInstance.ITEM_ID_STEP_SIZE ) );

    return items;
  }

  private Map<String, Object> requestFixedItems( ) throws HydPyServerException
  {
    if( m_fixedParameters.isEmpty() )
    {
      // REMARK: must be modifiable
      return new HashMap<>();
    }

    // REMARK: we need to initialize the 'fixedItems' in order to retrieve their state ni the next step.
    initializeState( HydPyServerManager.ANY_INSTANCE );

    // REMARK: HydPy always need instanceId; we give fake one here
    // REMARK: we do not request simulation-dates, as they will not be fixed in this sense.
    final Properties props = m_server.execute( HydPyServerManager.ANY_INSTANCE, METHODS_GET_FIXED_ITEMVALUES_OPENDA );

    /* pre-parse items */
    final Map<String, Object> values = preParseValues( props );

    /* only use those as 'fixed' that are really declared as those */
    values.keySet().retainAll( m_fixedParameters );

    return values;
  }

  @Override
  public IHydPyInstance createInstance( final String instanceId ) throws HydPyServerException
  {
    // needs to be called once per instanceId.
    initializeInstance( instanceId );

    return new HydPyServerInstance( instanceId, this );
  }

  /**
   * Tells HydPy to initialize the state for instanceId with the defined start values. Should be called exactly once per unique instanceId.
   */
  private void initializeInstance( final String instanceId ) throws HydPyServerException
  {
    initializeState( instanceId );

    // REMARK: special handling for the simulation-timegrid: we set the whole (aka init) timegrid as starting state for the simulation-timegrid
    // OpenDa will soon request all items and especially the start/stop time and expect the complete, yet unchanged, simulation-time

    /* build post-body for setting simulation dates */
    final StringBuffer body = new StringBuffer();
    body.append( IHydPyInstance.ITEM_ID_FIRST_DATE ).append( '=' ).append( m_firstDateValue ).append( '\r' ).append( '\n' );
    body.append( IHydPyInstance.ITEM_ID_LAST_DATE ).append( '=' ).append( m_lastDateValue ).append( '\r' ).append( '\n' );

    /* set simulation-dates */
    m_server.execute( instanceId, METHODS_SET_SIMULATIONDATES_OPENDA, body.toString() );
  }

  private void initializeState( final String instanceId ) throws HydPyServerException
  {
    log( "initializing state for instanceId = '%s'", instanceId );

    /* tell hyd py to write default values into state-register */
    m_server.execute( instanceId, METHODS_INITIALIZE_ITEMTYPES__OPENDA );
  }

  @Override
  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    log( "retrieving state for instanceId = '%s'", instanceId );

    final Properties props = m_server.execute( instanceId, METHODS_GET_ITEMVALUES_OPENDA );

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValues( props );
    preValues.putAll( m_fixedItems );

    /* fetch fixed items value necessary to parse timeseries */
    final Instant startTime = (Instant)preValues.get( IHydPyInstance.ITEM_ID_FIRST_DATE );
    final Instant endTime = (Instant)preValues.get( IHydPyInstance.ITEM_ID_LAST_DATE );

    /* parse item values */
    final List<IPrevExchangeItem> values = new ArrayList<>( preValues.size() );

    final Set<Entry<String, Object>> entrySet = preValues.entrySet();
    for( final Entry<String, Object> entry : entrySet )
    {
      final String id = entry.getKey();
      final AbstractServerItem item = getItem( id );

      final Object preValue = entry.getValue();

      final IPrevExchangeItem value = item.toExchangeItem( startTime, endTime, m_stepSeconds, preValue );
      values.add( value );

      final String valueText = item.printValue( value, m_stepSeconds );
      log( "%s=%s", id, valueText );
    }

    return values;
  }

  private Map<String, Object> preParseValues( final Properties props ) throws HydPyServerException
  {
    final Map<String, Object> preValues = new HashMap<>( props.size() );

    for( final String property : props.stringPropertyNames() )
    {
      final AbstractServerItem item = getItem( property );

      final String valueText = props.getProperty( property );

      final Object value = item.parseValue( valueText );
      preValues.put( property, value );
    }

    return preValues;
  }

  @Override
  public void setItemValues( final String instanceId, final Collection<IPrevExchangeItem> values ) throws HydPyServerException
  {
    log( "setting state for instanceId = '%s'", instanceId );

    final StringBuffer body = new StringBuffer();
    for( final IPrevExchangeItem exItem : values )
    {
      final String id = exItem.getId();

      final AbstractServerItem item = getItem( id );
      final String valueText = item.printValue( exItem, m_stepSeconds );

      body.append( id );
      body.append( '=' );
      body.append( valueText );
      body.append( '\r' );
      body.append( '\n' );

      log( "%s=%s", id, valueText );
    }

    m_server.execute( instanceId, METHODS_POST_ITEMVALUES__OPENDA, body.toString() );
  }

  @Override
  public void simulate( final String instanceId ) throws HydPyServerException
  {
    log( "running simulation for current state for instanceId = '%s'", instanceId );

    m_server.execute( instanceId, METHODS_GET_SIMULATE__OPENDA );
  }

  @Override
  public void shutdown( )
  {
    log( "%s: shutting down...%n", m_server.getName() );

    m_server.shutdown();

    log( "%s: shut down done%n", m_server.getName() );
  }

  @Override
  public void kill( )
  {
    m_server.kill();
  }
}