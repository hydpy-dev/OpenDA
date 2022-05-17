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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang3.Validate;
import org.hydpy.openda.server.HydPyServerClient.Poster;
import org.joda.time.Instant;
import org.openda.interfaces.IExchangeItem;

/**
 * Handles the OpenDA specific way on how to call a HydPy-Server.
 *
 * @author Gernot Belger
 */
final class HydPyOpenDACaller
{
  // REMARK: The life-cycle of the OpenDA model-instances enforce a very specific way, on
  // how the model must be called and specifically on how the internal model state must be
  // preserved and restored.

  private static final String METHODS_REQUEST_ITEMTYPES = //
      "GET_query_itemtypes"; //

  private static final String METHODS_REQUEST_INITIAL_STATE = //
      /* tell HydPy to write default values into state-register */
      "GET_register_initialitemvalues," + //
      /* and also query the initialization time grid (i.e. time span and step with which the HydPy is configured */
          "GET_query_initialisationtimegrid"; //

  private static final String METHODS_INITIALIZE_INSTANCE = //
      /* register default values into instance-state */
      "GET_register_initialitemvalues," + //
          "POST_register_simulationdates," + //
          /* and fetch them */
          "GET_query_itemvalues," + //
          "GET_query_simulationdates"; //

  private static final String METHODS_REGISTER_SERIESREADERDIR = "POST_register_seriesreaderdir";

  private static final String METHODS_REGISTER_SERIESWRITERDIR = "POST_register_serieswriterdir";

  private static final String METHODS_SET_AND_LOAD_CONDITIONS = //
      "POST_register_inputconditiondir," + //
          "GET_load_conditions"; //

  // IMPORTANT: register_simulationdates must be called before the rest,
  // as timeseries-items will be cut to exactly this time span.
  private static final String METHODS_REGISTER_ITEMVALUES = //
      "POST_register_simulationdates," + //
          "POST_register_changeitemvalues";

  private static final String METHODS_SIMULATE_AND_QUERY_ITEMVALUES = //
      /* activate current instance-state */
      "GET_activate_simulationdates," + //

          "GET_load_internalconditions," + //
          "GET_activate_changeitemvalues," + //

          /* run simulation */
          "GET_simulate," + //

          /* apply hydpy state to instance-state */
          "GET_deregister_internalconditions," + // REMARK: we need to delete the old state, else we might get memory problems in HydPY
          "GET_save_internalconditions," + //

          // TODO: check, why is there no GET_update_itemvalues as e.q. with query?
          "GET_update_conditionitemvalues," + //
          "GET_update_getitemvalues," + //
          "GET_update_inputitemvalues," + //
          "GET_update_outputitemvalues," + //

          /* and retrieve them directly */
          "GET_query_itemvalues," + //
          "GET_query_simulationdates"; //

  private static final String METHODS_SAVE_CONTROLPARAMETERS = //
      "POST_register_outputcontroldir," + //
          "GET_save_controls";

  private static final String METHODS_REQUEST_ITEMNAMES = "GET_query_itemsubnames"; //$NON-NLS-1$

  private static final String METHODS_WRITE_CONDITIONS = //
      "POST_register_outputconditiondir," + //$NON-NLS-1$
          "GET_save_conditions"; //$NON-NLS-1$

  private static final String ITEM_ID_FIRST_DATE_INIT = "firstdate_init"; //$NON-NLS-1$

  private static final String ITEM_ID_LAST_DATE_INIT = "lastdate_init"; //$NON-NLS-1$

  private static final String ITEM_ID_STEP_SIZE = "stepsize"; //$NON-NLS-1$

  private static final String ARGUMENT_SERIESREADERDIR = "seriesreaderdir"; //$NON-NLS-1$

  private static final String ARGUMENT_SERIESWRITERDIR = "serieswriterdir"; //$NON-NLS-1$

  private static final String ARGUMENT_OUTPUTCONDITIONDIR = "outputconditiondir"; //$NON-NLS-1$

  private static final String ARGUMENT_INPUTCONDITIONDIR = "inputconditiondir"; //$NON-NLS-1$

  private static final String ARGUMENT_OUTPUTCONTROLDIR = "outputcontroldir"; //$NON-NLS-1$

  /**
   * For items where we know that all hydpy instances will report the same initial state, we only parse those once and reuse them for other instances.
   * This improves performance a lot for long model runs.
   */
  private static final Map<String, Object> SHARED_INITIAL_STATE = new HashMap<>();

  private final Map<String, HydPyExchangeCache> m_instanceCaches = new HashMap<>();

  private Map<String, String[]> m_itemNames = null;

  private final String m_name;

  private final HydPyServerClient m_client;

  private final Map<String, AbstractServerItem< ? >> m_itemIndex;

  private final long m_stepSeconds;

  private final String m_firstDateValue;

  private final String m_lastDateValue;

  public HydPyOpenDACaller( final String name, final HydPyServerClient client ) throws HydPyServerException
  {
    m_name = name;
    m_client = client;

    final List<AbstractServerItem< ? >> items = requestItems();

    /* Retrieve initial state and also init-dates and stepsize */
    // REMARK: HydPy always need instanceId; we give fake one here
    m_client.debugOut( m_name, "requesting fixed item states and initial time-grid" );
    final Properties props = m_client.callGet( HydPyServerManager.ANY_INSTANCE, METHODS_REQUEST_INITIAL_STATE );

    m_firstDateValue = props.getProperty( ITEM_ID_FIRST_DATE_INIT );
    m_lastDateValue = props.getProperty( ITEM_ID_LAST_DATE_INIT );
    final String stepValue = props.getProperty( ITEM_ID_STEP_SIZE );

    /* remove time grid related, as they do not represent items */
    props.remove( ITEM_ID_FIRST_DATE_INIT );
    props.remove( ITEM_ID_LAST_DATE_INIT );
    props.remove( ITEM_ID_STEP_SIZE );

    /* add fixed grid items and determine step seconds */
    final AbstractServerItem<Long> stepItem = AbstractServerItem.newDurationItem( HydPyModelInstance.ITEM_ID_STEP_SIZE );
    m_stepSeconds = stepItem.parseValue( null, null, 0l, stepValue );

    /* REMARK: HydPy thinks in time interval, but OpenDA does not. We always adjust by one timestep when reading/writing to/from HydPy */
    items.add( AbstractServerItem.newTimeItem( HydPyModelInstance.ITEM_ID_FIRST_DATE ) );
    items.add( AbstractServerItem.newTimeItem( HydPyModelInstance.ITEM_ID_LAST_DATE ) );
    items.add( stepItem );

    /* build item index */
    // REMARK: using a tree map here, so item are later always written in same order.
    final SortedMap<String, AbstractServerItem< ? >> itemIndex = new TreeMap<>();
    for( final AbstractServerItem< ? > item : items )
      itemIndex.put( item.getId(), item );
    m_itemIndex = Collections.unmodifiableSortedMap( itemIndex );
  }

  public String getName( )
  {
    return m_name;
  }

  private <TYPE> AbstractServerItem<TYPE> getItem( final String id )
  {
    @SuppressWarnings( "unchecked" ) final AbstractServerItem<TYPE> item = (AbstractServerItem<TYPE>)m_itemIndex.get( id );

    if( item == null )
      throw new IllegalArgumentException( String.format( "Invalid item id: %s", id ) );

    return item;
  }

  private List<AbstractServerItem< ? >> requestItems( ) throws HydPyServerException
  {
    final Properties props = m_client.callGet( null, METHODS_REQUEST_ITEMTYPES );

    final List<AbstractServerItem< ? >> items = new ArrayList<>( props.size() );

    for( final String itemId : props.stringPropertyNames() )
    {
      final String value = props.getProperty( itemId );

      final String[] itemNames = getItemNames( itemId );

      final AbstractServerItem< ? > item = AbstractServerItem.fromHydPyType( itemId, value, itemNames );
      items.add( item );
    }

    return items;
  }

  public Collection<HydPyExchangeItemDescription> getItems( )
  {
    final Collection<HydPyExchangeItemDescription> allDescriptions = new ArrayList<>();

    for( final AbstractServerItem< ? > item : m_itemIndex.values() )
    {
      final Collection<HydPyExchangeItemDescription> itemDescriptions = item.getExchangeItemDescriptions();
      allDescriptions.addAll( itemDescriptions );
    }

    return allDescriptions;
  }

  /**
   * Tells HydPy to initialize the state for instanceId with the defined start values. Should be called exactly once per unique instanceId.
   */
  public List<IExchangeItem> initializeInstance( final String instanceId, final HydPyInstanceDirs instanceDirs ) throws HydPyServerException
  {
    m_client.debugOut( m_name, "initializing state for instanceId = '%s'", instanceId );

    // REMARK: special handling for the simulation-timegrid: we set the whole (aka init) timegrid as starting state for the simulation-timegrid
    // OpenDa will soon request all items and especially the start/stop time and expect the complete, yet unchanged, simulation-time

    /* register seriesWriterDir; HydPy will automatically write series if configured in hydpy.xml */
    final File seriesWriterDir = instanceDirs.getSeriesWriterDir();
    if( seriesWriterDir != null )
    {
      m_client.callPost( instanceId, METHODS_REGISTER_SERIESWRITERDIR ) //
          .body( ARGUMENT_SERIESWRITERDIR, seriesWriterDir.getAbsolutePath() ) //
          .execute();
    }

    /* Load conditions but only if they exist */
    final File inputConditionsDir = instanceDirs.getInputConditionsDir();
    if( inputConditionsDir != null )
    {
      // REMARK: OpenDa will purge all old instance dirs, so we cannot reuse that structure
      m_client.callPost( instanceId, METHODS_SET_AND_LOAD_CONDITIONS ) //
          .body( ARGUMENT_INPUTCONDITIONDIR, inputConditionsDir.toString() ) //
          .execute();
    }

    /* load series */
    final File seriesReaderDir = instanceDirs.getSeriesReaderDir();
    if( seriesReaderDir != null )
    {
      m_client.callPost( instanceId, METHODS_REGISTER_SERIESREADERDIR ) //
          .body( ARGUMENT_SERIESREADERDIR, seriesReaderDir.toString() ) //
          .execute();
    }

    // FIXME: we fetch for every instance the full set of item-values and keep them as initial state.
    // This can be quite costly for long model runs / models with many elements
    // However in most cases, the initial state could be shared between all instances.
    // So todo:
    // - make items (in hydpy xml?) which are initially the same for all instances
    // - fetch those only once
    // - share these between model instances

    /*
     * set simulation-dates
     * We always set the initial simulation dates to the init dates of HydPy, as OpenDa we request those to initialize their simulation-range
     */
    final Properties props = m_client.callPost( instanceId, METHODS_INITIALIZE_INSTANCE ) //
        .body( HydPyModelInstance.ITEM_ID_FIRST_DATE, m_firstDateValue ) //
        .body( HydPyModelInstance.ITEM_ID_LAST_DATE, m_lastDateValue ) //
        .execute();

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValuesOrGetShared( props, SHARED_INITIAL_STATE );

    final HydPyExchangeCache instanceCache = new HydPyExchangeCache( preValues );
    m_instanceCaches.put( instanceId, instanceCache );
    return parseItemValues( instanceCache, preValues );
  }

  private List<IExchangeItem> parseItemValues( final HydPyExchangeCache instanceCache, final Map<String, Object> preValues )
  {
    // REMARK: set 'stepSeconds' as fixed item, as these are not returned from HydPy from any state
    preValues.put( ITEM_ID_STEP_SIZE, m_stepSeconds );

    /* parse item values */
    final List<IExchangeItem> values = new ArrayList<>( preValues.size() );

    final Set<Entry<String, Object>> entrySet = preValues.entrySet();
    for( final Entry<String, Object> entry : entrySet )
    {
      final String id = entry.getKey();
      final Object preValue = entry.getValue();

      final AbstractServerItem<Object> item = getItem( id );

      final List<IExchangeItem> exItems = instanceCache.parseItemValue( item, preValue );
      values.addAll( exItems );
    }

    return values;
  }

  private Map<String, Object> preParseValuesOrGetShared( final Properties props, final Map<String, Object> sharedState ) throws HydPyServerException
  {
    final Instant startTime = new Instant( props.get( HydPyModelInstance.ITEM_ID_FIRST_DATE ) );
    final Instant endTime = new Instant( props.get( HydPyModelInstance.ITEM_ID_LAST_DATE ) );

    final Map<String, Object> preValues = new TreeMap<>();

    for( final String property : props.stringPropertyNames() )
    {
      final String valueText = props.getProperty( property );

      final Object value = preParseValueOrGetShared( property, valueText, sharedState, startTime, endTime );
      preValues.put( property, value );
    }

    return preValues;
  }

  private Object preParseValueOrGetShared( final String property, final String valueText, final Map<String, Object> sharedState, final Instant startTime, final Instant endTime ) throws HydPyServerException
  {
    final AbstractServerItem<Object> item = getItem( property );

    /* clone from shared state (only during initialization) if it is shared */
    final String id = item.getId();
    if( item.isInitialStateShared() && sharedState != null && sharedState.containsKey( id ) )
    {
      final Object sharedValue = sharedState.get( id );
      return item.copy( sharedValue );
    }

    /* really parse the value */
    final Object value = item.parseValue( startTime, endTime, m_stepSeconds, valueText );

    /* remember in shared state if it is the first time */
    if( item.isInitialStateShared() && sharedState != null )
      sharedState.put( id, value );

    return value;
  }

  public void setItemValues( final String instanceId, final Collection<IExchangeItem> values ) throws HydPyServerException
  {
    m_client.debugOut( m_name, "setting state for instanceId = '%s'", instanceId );

    final Map<String, IExchangeItem> allExItems = new HashMap<>();
    for( final IExchangeItem item : values )
      allExItems.put( item.getId(), item );

    /* fetch current time range */
    final Instant currentStartTime = toTime( allExItems, HydPyModelInstance.ITEM_ID_FIRST_DATE );
    // REMARK: adjust by one step, as HydPy thinks in intervals
    final Instant currentStartTimeNextStep = currentStartTime.plus( m_stepSeconds * 1000 );
    final Instant currentEndTime = toTime( allExItems, HydPyModelInstance.ITEM_ID_LAST_DATE );

    final HydPyExchangeCache instanceCache = m_instanceCaches.get( instanceId );

    final Poster caller = m_client.callPost( instanceId, METHODS_REGISTER_ITEMVALUES );

    for( final AbstractServerItem< ? > serverItem : m_itemIndex.values() )
    {
      final List<IExchangeItem> exItems = getItemsFor( serverItem, allExItems );
      if( exItems != null )
      {
        final String valueText = instanceCache.printItemValue( serverItem, exItems, currentStartTimeNextStep, currentEndTime );
        caller.body( serverItem.getId(), valueText );
      }
    }

    caller.execute();
  }

  private List<IExchangeItem> getItemsFor( final AbstractServerItem< ? > serverItem, final Map<String, IExchangeItem> exItems ) throws HydPyServerException
  {
    final Collection<HydPyExchangeItemDescription> descriptions = serverItem.getExchangeItemDescriptions();

    final List<IExchangeItem> result = new ArrayList<>( descriptions.size() );

    for( final HydPyExchangeItemDescription description : descriptions )
    {
      final IExchangeItem exItem = exItems.get( description.getId() );
      if( exItem != null )
        result.add( exItem );
    }

    if( result.size() == 0 )
    {
      /* this is ok; item is completely not written */
      return null;
    }

    if( result.size() != descriptions.size() )
    {
      /* no ok: trying to write only part of a server item */
      throw new HydPyServerException( "A composed server item must write either all parts or none. some exchange items are missing" );
    }

    /* ok, all expected exchange items are present */
    return result;
  }

  private Instant toTime( final Map<String, IExchangeItem> sortedItems, final String itemId )
  {
    final IExchangeItem exItem = sortedItems.get( itemId );
    final AbstractServerItem<Instant> serverItem = getItem( HydPyModelInstance.ITEM_ID_FIRST_DATE );
    return serverItem.toValue( Collections.singletonList( exItem ) );
  }

  public synchronized String[] getItemNames( final String itemId ) throws HydPyServerException
  {
    if( m_itemNames == null )
    {
      m_itemNames = new HashMap<>();

      final Properties props = m_client.callGet( null, METHODS_REQUEST_ITEMNAMES );

      /* fetch and parse and the names */
      final Set<String> itemIds = props.stringPropertyNames();
      for( final String item : itemIds )
      {
        final String value = props.getProperty( item );
        final String[] itemNames = HydPyUtils.parseStringArray( value );
        /* we cache them, because they never change, and we get them all at once */
        m_itemNames.put( item, itemNames );
      }
    }

    final String[] itemNames = m_itemNames.get( itemId );
    if( itemNames == null )
      throw new HydPyServerException( String.format( "No sub-element names found for item: %s", itemId ) );

    return itemNames;
  }

  public List<IExchangeItem> simulate( final String instanceId, final File outputControlDir ) throws HydPyServerException
  {
    m_client.debugOut( m_name, "running simulation for current state for instanceId = '%s'", instanceId );

    final Properties props = m_client.callGet( instanceId, METHODS_SIMULATE_AND_QUERY_ITEMVALUES );

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValuesOrGetShared( props, null );

    final HydPyExchangeCache instanceCache = m_instanceCaches.get( instanceId );
    final List<IExchangeItem> simulationResult = parseItemValues( instanceCache, preValues );

    if( outputControlDir != null )
    {
      m_client.callPost( instanceId, METHODS_SAVE_CONTROLPARAMETERS ) //
          .body( ARGUMENT_OUTPUTCONTROLDIR, outputControlDir.getAbsolutePath() ) //
          .execute();
    }

    return simulationResult;
  }

  public void writeConditions( final String instanceId, final File outputConditionsDir ) throws HydPyServerException
  {
    Validate.notNull( outputConditionsDir );

    m_client.callPost( instanceId, METHODS_WRITE_CONDITIONS ) //
        .body( ARGUMENT_OUTPUTCONDITIONDIR, outputConditionsDir.getAbsolutePath() ) //
        .execute();
  }

  public void shutdown( )
  {
    m_client.debugOut( m_name, "shutting down..." );
    m_client.shutdown();
  }
}