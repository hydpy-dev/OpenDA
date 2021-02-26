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
final class HydPyServerOpenDA
{
  // REMARK: The life-cycle of the OpenDA model-instances enforce a very specific way, on
  // how the model must be called and specifically on how the internal model state must be
  // preserved and restored.

  private static final String METHODS_REQUEST_ITEMTYPES = //
      "GET_query_itemtypes"; //

  private static final String METHODS_REQUEST_INITIAL_STATE = //
      /* tell hyd py to write default values into state-register */
      "GET_register_initialitemvalues," + //
      /* and query them */
          "GET_query_conditionitemvalues," + //
          "GET_query_getitemvalues," + //
          "GET_query_parameteritemvalues," + //
          "GET_query_initialisationtimegrid"; //

  private static final String METHODS_INITIALIZE_INSTANCE = //
      /* register default values into instance-state */
      "GET_register_initialitemvalues," + //
          "POST_register_simulationdates," + //
          /* and fetch them */
          "GET_query_conditionitemvalues," + //
          "GET_query_getitemvalues," + //
          "GET_query_parameteritemvalues," + //
          "GET_query_simulationdates"; //

  private static final String METHODS_REGISTER_ITEMVALUES = //
      "POST_register_simulationdates," + //
          "POST_register_parameteritemvalues," + //
          "POST_register_conditionitemvalues";

  private static final String METHODS_SIMULATE_AND_QUERY_ITEMVALUES = //
      /* activate current instance-state */
      "GET_activate_simulationdates," + //
          "GET_activate_parameteritemvalues," + //
          "GET_load_internalconditions," + //
          "GET_activate_conditionitemvalues," + //
          /* run simulation */
          "GET_simulate," + //
          /* apply hydpy state to instance-state */
          "GET_save_internalconditions," + //
          "GET_update_conditionitemvalues," + //
          "GET_update_getitemvalues," + //
          /* and retrieve them directly */
          "GET_query_conditionitemvalues," + //
          "GET_query_getitemvalues," + //
          "GET_query_parameteritemvalues," + //
          "GET_query_simulationdates"; //

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

    /* Retrieve initial state and also init-dates and stepsize */
    // REMARK: HydPy always need instanceId; we give fake one here
    log( "requesting fixed item states and initial time-grid" );
    final Properties props = m_server.execute( HydPyServerManager.ANY_INSTANCE, METHODS_REQUEST_INITIAL_STATE );

    m_firstDateValue = props.getProperty( ITEM_ID_FIRST_DATE_INIT );
    m_lastDateValue = props.getProperty( ITEM_ID_LAST_DATE_INIT );
    final String stepValue = props.getProperty( ITEM_ID_STEP_SIZE );

    /* remove time grid related, as they do not represent items */
    props.remove( ITEM_ID_FIRST_DATE_INIT );
    props.remove( ITEM_ID_LAST_DATE_INIT );
    props.remove( ITEM_ID_STEP_SIZE );
    m_fixedItems = getFixedItemValues( props );

    /* detemrine step seconds */
    final AbstractServerItem stepServerItem = getItem( ITEM_ID_STEP_SIZE );
    final Object stepParsedValue = stepServerItem.parseValue( stepValue );
    final IPrevExchangeItem stepItem = stepServerItem.toExchangeItem( null, null, 0, stepParsedValue );
    m_stepSeconds = (long)((DoubleExchangeItem)stepItem).getValue();

    // REMARK: set 'stepSeconds' as fixed item, as these are not return from HydPy from any state
    m_fixedItems.put( ITEM_ID_STEP_SIZE, m_stepSeconds );
  }

  public String getName( )
  {
    return m_server.getName();
  }

  /**
   * For debug purposes only
   */
  private void log( final String message, final Object... arguments )
  {
    final String msg = String.format( message, arguments );

    // TODO: use logging framework or something
    System.out.print( getName() );
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

  public List<IServerItem> getItems( )
  {
    return m_items;
  }

  private List<IServerItem> requestItems( ) throws HydPyServerException
  {
    final Properties props = m_server.execute( null, METHODS_REQUEST_ITEMTYPES );

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

  private Map<String, Object> getFixedItemValues( final Properties props ) throws HydPyServerException
  {
    /* pre-parse items */
    final Map<String, Object> values = preParseValues( props );

    /* only use those as 'fixed' that are really declared as those */
    values.keySet().retainAll( m_fixedParameters );

    return values;
  }

  /**
   * Tells HydPy to initialize the state for instanceId with the defined start values. Should be called exactly once per unique instanceId.
   */
  public List<IPrevExchangeItem> initializeInstance( final String instanceId ) throws HydPyServerException
  {
    log( "initializing state for instanceId = '%s'", instanceId );

    // REMARK: special handling for the simulation-timegrid: we set the whole (aka init) timegrid as starting state for the simulation-timegrid
    // OpenDa will soon request all items and especially the start/stop time and expect the complete, yet unchanged, simulation-time

    /* build post-body for setting simulation dates */
    final StringBuffer body = new StringBuffer();
    body.append( IHydPyInstance.ITEM_ID_FIRST_DATE ).append( '=' ).append( m_firstDateValue ).append( '\r' ).append( '\n' );
    body.append( IHydPyInstance.ITEM_ID_LAST_DATE ).append( '=' ).append( m_lastDateValue ).append( '\r' ).append( '\n' );

    /* set simulation-dates */
    final Properties props = m_server.execute( instanceId, METHODS_INITIALIZE_INSTANCE, body.toString() );
    return parseItemValues( props );
  }

  private List<IPrevExchangeItem> parseItemValues( final Properties props ) throws HydPyServerException
  {
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

    m_server.execute( instanceId, METHODS_REGISTER_ITEMVALUES, body.toString() );
  }

  public List<IPrevExchangeItem> simulate( final String instanceId ) throws HydPyServerException
  {
    log( "running simulation for current state for instanceId = '%s'", instanceId );

    final Properties props = m_server.execute( instanceId, METHODS_SIMULATE_AND_QUERY_ITEMVALUES );
    return parseItemValues( props );
  }

  public void shutdown( )
  {
    log( "shutting down..." );

    m_server.shutdown();

    log( "shut down done" );
  }

  public void kill( )
  {
    m_server.kill();
  }
}