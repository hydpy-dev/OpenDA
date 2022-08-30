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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final Map<String, Instant> m_lastSimulationEndTimes = new HashMap<>();

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
    final Properties props = m_client.get( HydPyServerManager.ANY_INSTANCE ) //
        /* and also query the initialization time grid (i.e. time span and step with which the HydPy is configured */
        .method( "GET_query_initialisationtimegrid" ) //
        .execute();

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
    final Properties props = m_client.get( null ) //
        .method( "GET_query_itemtypes" ) //
        .execute();

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

    final Poster caller = m_client.post( instanceId );

    // REMARK: special handling for the simulation-timegrid: we set the whole (aka init) timegrid as starting state for the simulation-timegrid
    // OpenDa will soon request all items and especially the start/stop time and expect the complete, yet unchanged, simulation-time

    /* register seriesWriterDir; HydPy will automatically write series if configured in hydpy.xml */
    final File seriesWriterDir = instanceDirs.getSeriesWriterDir();
    if( seriesWriterDir != null )
    {
      caller.method( "POST_register_serieswriterdir" ) //
          .body( ARGUMENT_SERIESWRITERDIR, seriesWriterDir.getAbsolutePath() ); //
    }

    /* register directory for reading series */
    final File seriesReaderDir = instanceDirs.getSeriesReaderDir();
    if( seriesReaderDir != null )
    {
      caller.method( "POST_register_seriesreaderdir" ) //
          .body( ARGUMENT_SERIESREADERDIR, seriesReaderDir.toString() ); //
    }

    /* Load conditions but only if they exist */
    final File inputConditionsDir = instanceDirs.getInputConditionsDir();
    if( inputConditionsDir != null )
    {
      /* unzip if necessary */
      final Path realInputConditionsDir = prepareInputConditionsDir( inputConditionsDir );

      // REMARK: OpenDa will purge all old instance dirs, so we cannot reuse that structure
      caller.method( "POST_register_inputconditiondir" ) //
          .body( ARGUMENT_INPUTCONDITIONDIR, realInputConditionsDir.toString() ) //
          .method( "GET_load_conditions" ) //
          .method( "GET_save_internalconditions" ) //
          .method( "GET_update_conditionitemvalues" ) //

          // REMARK: actually everything from "GET_register_initialchangeitemvalues" except "GET_register_initialconditionitemvalues"
          .method( "GET_register_initialparameteritemvalues" ) //
          .method( "GET_register_initialinputitemvalues" ) //
          .method( "GET_register_initialoutputitemvalues" ) //
          .method( "GET_register_initialgetitemvalues" );

      if( !inputConditionsDir.toPath().equals( realInputConditionsDir ) )
        FileDeletionThread.instance().addFilesForDeletion( Collections.singletonList( realInputConditionsDir.toFile() ) );
    }
    else
      /* register default values into instance-state if we did not load them ourself */
      caller.method( "GET_register_initialitemvalues" ); //

    final Properties props = caller //

        /*
         * set simulation-dates
         * We always set the initial simulation dates to the init dates of HydPy, as OpenDa we request those to initialize their simulation-range
         */
        .method( "POST_register_simulationdates" ) //
        .body( HydPyModelInstance.ITEM_ID_FIRST_DATE, m_firstDateValue ) //
        .body( HydPyModelInstance.ITEM_ID_LAST_DATE, m_lastDateValue ) //

        /* and fetch them */
        // FIXME: we already share item values between instances if they are marked as such (.shared)
        // but we still request them, because we can't request individual items
        .method( "GET_query_itemvalues" ) //
        .method( "GET_query_simulationdates" ) //

        .execute();

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValuesOrGetShared( props, SHARED_INITIAL_STATE );

    final HydPyExchangeCache instanceCache = new HydPyExchangeCache( preValues );
    m_instanceCaches.put( instanceId, instanceCache );
    return parseItemValues( instanceCache, preValues );
  }

  private Path prepareInputConditionsDir( final File inputConditionsDir )
  {
    if( inputConditionsDir.isDirectory() )
      return inputConditionsDir.toPath();

    if( inputConditionsDir.isFile() )
    {
      // REMARK: if we got a file, we assume it's a zip.
      // We do not want hydpy to unzip the file itself, because it will delete the zip.
      try
      {
        final Path tempDir = Files.createTempDirectory( "hydpyconditions_loading" );
        HydPyUtils.unzipConditions( inputConditionsDir.toPath(), tempDir );
        return tempDir;
      }
      catch( final IOException e )
      {
        e.printStackTrace();

        final String message = "Failed to unzip input file for conditions: " + inputConditionsDir;
        throw new HydPyServerException( message );
      }
    }

    final String message = "input directory/file for conditions does not exist: " + inputConditionsDir;
    throw new HydPyServerException( message );
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

    final Poster caller = m_client.post( instanceId ) //
        // IMPORTANT: register_simulationdates must be called before the rest,
        // as timeseries-items will be cut to exactly this time span.
        .method( "POST_register_simulationdates" ) //
        .method( "POST_register_changeitemvalues" ); //

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

      final Properties props = m_client.get( null ) //
          .method( "GET_query_itemsubnames" ) //
          .execute();

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

  public List<IExchangeItem> restoreInternalState( final String instanceId, final File stateConditionsDir ) throws HydPyServerException
  {
    final Poster caller = m_client.post( instanceId );

    if( stateConditionsDir != null )
    {
      caller //
          .method( "POST_register_inputconditiondir" ) //
          .body( ARGUMENT_INPUTCONDITIONDIR, stateConditionsDir.getAbsolutePath() ); //
    }

    final Properties props = caller //
        .method( "GET_load_conditions" ) //
        .method( "GET_save_internalconditions" ) //
        .method( "GET_update_conditionitemvalues" ) //
        .method( "GET_query_itemvalues" ) //
        .method( "GET_query_simulationdates" ) //
        .execute();

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValuesOrGetShared( props, null );

    final HydPyExchangeCache instanceCache = m_instanceCaches.get( instanceId );
    return parseItemValues( instanceCache, preValues );
  }

  public List<IExchangeItem> simulate( final String instanceId, final File outputControlDir ) throws HydPyServerException
  {
    m_client.debugOut( m_name, "running simulation for current state for instanceId = '%s'", instanceId );

    final Poster caller = m_client.post( instanceId );

    caller //
        /* activate current instance-state */
        .method( "GET_activate_simulationdates" ) //
        .method( "GET_load_internalconditions" ) //
        .method( "GET_activate_changeitemvalues" ) //

        /* run simulation */
        .method( "GET_simulate" ) //

        /* apply hydpy state to instance-state */
        .method( "GET_deregister_internalconditions" ); // REMARK: we need to delete the old state, else we might get memory problems in HydPY

    // REMARK: we always save the conditions also internally, to keep the state consistent
    caller //
        .method( "GET_save_internalconditions" ) //

        // TODO: check, why is there no GET_update_itemvalues as e.q. with query?
        .method( "GET_update_conditionitemvalues" ) //
        .method( "GET_update_getitemvalues" ) //
        .method( "GET_update_inputitemvalues" ) //
        .method( "GET_update_outputitemvalues" ) //

        /* and retrieve them directly */
        .method( "GET_query_itemvalues" ) //
        .method( "GET_query_simulationdates" ); //

    if( outputControlDir != null )
    {
      caller //
          .method( "POST_register_outputcontroldir" ) //
          .method( "GET_save_controls" ) //
          .body( ARGUMENT_OUTPUTCONTROLDIR, outputControlDir.getAbsolutePath() );
    }

    final Properties props = caller.execute();

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValuesOrGetShared( props, null );

    /* remember last simulation end time for potential following calls to writeConditions */
    final Instant endSimulationTime = (Instant)preValues.get( HydPyModelInstance.ITEM_ID_LAST_DATE );
    m_lastSimulationEndTimes.put( instanceId, endSimulationTime );

    final HydPyExchangeCache instanceCache = m_instanceCaches.get( instanceId );
    final List<IExchangeItem> simulationResult = parseItemValues( instanceCache, preValues );

    return simulationResult;
  }

  public void writeConditions( final String instanceId, final File outputConditionsDir ) throws HydPyServerException
  {
    Validate.notNull( outputConditionsDir );

    // REMARK: we must set the current starting time to the last simulated time, as load_internalconditions
    // will load from the currently set start time, which is exactly present for the last simulated end time.
    final Instant startTime = m_lastSimulationEndTimes.get( instanceId );
    if( startTime == null )
    {
      System.err.format( "OpenDa tries to save the model state before any simulations was run. please check your analysis times.%n" );
      return;
    }

    // REMARK: the exact value of end time is irrelevant in these calls, so we simply use the start time + plus one time step (hydpy complains if they are the same)
    final Instant endTime = startTime.plus( m_stepSeconds * 1000 );

    final TimeItem startItem = (TimeItem)m_itemIndex.get( HydPyModelInstance.ITEM_ID_FIRST_DATE );
    final TimeItem endItem = (TimeItem)m_itemIndex.get( HydPyModelInstance.ITEM_ID_LAST_DATE );
    final String startTimeText = startItem.printValue( startTime );
    final String endTimeText = startItem.printValue( endTime );

    /* we also need the current simulation range in order to restare it later */
    final Properties simulationRangeProperties = m_client.get( instanceId ) //
        .method( "GET_activate_simulationdates" ) //
        .method( "GET_query_simulationdates" ) //
        .execute();
    final String originalStartTimeText = simulationRangeProperties.getProperty( HydPyModelInstance.ITEM_ID_FIRST_DATE );
    final String originalEndTimeText = simulationRangeProperties.getProperty( HydPyModelInstance.ITEM_ID_LAST_DATE );

    m_client.post( instanceId ) //
        /* set simulation range to fake range */
        .method( "POST_register_simulationdates" ) //
        .body( startItem.getId(), startTimeText ) //
        .body( endItem.getId(), endTimeText ) //
        .method( "GET_activate_simulationdates" ) //

        /* restore conditions for current instance and save those */
        .method( "GET_load_internalconditions" ) //

        .method( "GET_activate_changeitemvalues" ) //

        .method( "POST_register_outputconditiondir" ) //
        .body( ARGUMENT_OUTPUTCONDITIONDIR, outputConditionsDir.getAbsolutePath() ) //
        .method( "GET_save_conditions" ) //

        .execute();

    /* set simulation range to fake range, because anytime (e.g. during restoreInternalState) a query_items may occur */
    // REMARK: must be a separate call because the item-id's in the body are the same.
    m_client.post( instanceId ) //
        .method( "POST_register_simulationdates" ) //
        .body( startItem.getId(), originalStartTimeText ) //
        .body( endItem.getId(), originalEndTimeText ) //
        .method( "GET_activate_simulationdates" ) //
        .execute();
  }

  public void shutdown( )
  {
    m_client.debugOut( m_name, "shutting down..." );
    m_client.shutdown();
  }
}