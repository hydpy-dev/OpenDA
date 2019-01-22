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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.joda.time.Instant;
import org.openda.exchange.DoubleExchangeItem;
import org.openda.interfaces.IPrevExchangeItem;

/**
 * Represents a HydPyServer on the client side.
 *
 * @author Gernot Belger
 */
public final class HydPyServer
{
  private static final String PATH_STATUS = "status"; //$NON-NLS-1$

  private static final String PATH_EXECUTE = "execute"; //$NON-NLS-1$

  private static final String PATH_CLOSE_SERVER = "close_server"; //$NON-NLS-1$

  private static final String PARAMETER_INSTANCE_NUMBER = "id"; //$NON-NLS-1$

  private static final String PARAMETER_METHODS = "methods"; //$NON-NLS-1$

  public static final String ITEM_ID_FIRST_DATE = "firstdate"; //$NON-NLS-1$

  public static final String ITEM_ID_LAST_DATE = "lastdate"; //$NON-NLS-1$

  public static final String ITEM_ID_STEP_SIZE = "stepsize"; //$NON-NLS-1$

  // REMARK: The life-cycle of the OpenDA model-instances enforce a very specific way, on
  // how the model must be called and specifically on how the internal model state must be
  // preserved and restored.

  private static final String METHODS_GET_ITEMTYPES__OPENDA = //
      "GET_parameteritemtypes," + //
          "GET_conditionitemtypes," + //
          "GET_getitemtypes";

  private static final String METHODS_GET_SIMULATE__OPENDA = //
      "GET_simulate," + //
          "GET_save_timegrid," + //
          "GET_save_parameteritemvalues," + //
          "GET_save_conditionvalues," + //
          "GET_save_modifiedconditionitemvalues," + //
          "GET_save_getitemvalues"; //

  private static final String METHODS_GET_ITEMVALUES__OPENDA = //
      "GET_savedtimegrid," + //
          "GET_savedparameteritemvalues," + //
          "GET_savedmodifiedconditionitemvalues," + //
          "GET_savedgetitemvalues";

  private static final String METHODS_POST_ITEMVALUES__OPENDA = //
      "POST_timegrid," + //
          "POST_parameteritemvalues," + //
          "GET_load_conditionvalues," + //
          "POST_conditionitemvalues";

  private final URI m_address;

  private final Process m_process;

  private final int m_timeoutMillis = 60000;

  private List<AbstractServerItem> m_items = null;

  private Map<String, AbstractServerItem> m_itemIndex = null;

  private Map<String, Object> m_fixedItems;

  private final Collection<String> m_fixedParameters;

  public HydPyServer( final URI address, final Process process, final Collection<String> fixedParameters )
  {
    m_address = address;
    m_process = process;
    m_fixedParameters = fixedParameters;
  }

  private void checkProcess( ) throws HydPyServerProcessException
  {
    try
    {
      m_process.exitValue();
      throw new HydPyServerProcessException( "HydPy Server unexpectedly terminated" );
    }
    catch( final IllegalThreadStateException e )
    {
      // Process is still living, return normally
      return;
    }
  }

  void killServer( )
  {
    try
    {
      m_process.destroy();
    }
    catch( final Exception e )
    {
      e.printStackTrace();
    }
  }

  private HttpEntity callGet( final URI endpoint, final int timeout ) throws HydPyServerException
  {
    try
    {
      checkProcess();

      final Request request = Request.Get( endpoint );

      return callServer( request, timeout );
    }
    catch( final HydPyServerProcessException e )
    {
      e.printStackTrace();
      throw new HydPyServerException( "HydPy-Server not running", e );
    }
  }

  private Properties callGetAndParse( final URI endpoint, final int timeoutMillis ) throws HydPyServerException
  {
    final HttpEntity entity = callGet( endpoint, timeoutMillis );

    try
    {
      final Properties props = new Properties();
      try( final InputStream content = entity.getContent() )
      {
        props.load( content );
        return props;
      }
    }
    catch( final Exception e )
    {
      e.printStackTrace();

      throw new HydPyServerException( "Failed to read HydPy-Server response", e );
    }
  }

  private URI buildEndpoint( final String path, final String instanceId, final String methods ) throws HydPyServerException
  {
    try
    {
      final URIBuilder builder = new URIBuilder( m_address ).setPath( path );
      if( instanceId != null )
        builder.addParameter( PARAMETER_INSTANCE_NUMBER, instanceId );
      if( methods != null )
        builder.addParameter( PARAMETER_METHODS, methods );

      return builder.build();
    }
    catch( final URISyntaxException e )
    {
      throw new HydPyServerException( "Invalid uri", e );
    }
  }

  private HttpEntity callPost( final URI endpoint, final int timeout, final String body ) throws HydPyServerException
  {
    try
    {
      checkProcess();

      final ContentType contentType = ContentType.TEXT_PLAIN.withCharset( StandardCharsets.UTF_8 );

      final Request request = Request.Post( endpoint ).bodyString( body, contentType );

      return callServer( request, timeout );
    }
    catch( final HydPyServerProcessException e )
    {
      e.printStackTrace();
      throw new HydPyServerException( "HydPy-Server not running", e );
    }
  }

  private HttpEntity callServer( final Request request, final int timeout ) throws HydPyServerException
  {
    try
    {
      final HttpResponse response = request.connectTimeout( timeout ).execute().returnResponse();
      final StatusLine statusLine = response.getStatusLine();
      final int statusCode = statusLine.getStatusCode();
      if( statusCode != HttpStatus.SC_OK )
      {
        final String message = String.format( "HydPy-Server returned with invalid code: %s / %s", statusLine.getStatusCode(), statusLine.getReasonPhrase() );
        throw new HydPyServerException( message );
      }

      return response.getEntity();
    }
    catch( final Exception e )
    {
      throw new HydPyServerException( "Failed to connect to HydPy-Server", e );
    }
  }

  private AbstractServerItem getItem( final String id ) throws HydPyServerException
  {
    getItems();

    if( m_itemIndex == null )
      throw new IllegalStateException();

    final AbstractServerItem item = m_itemIndex.get( id );

    if( item == null )
      throw new IllegalArgumentException( String.format( "Invalid item id: %s", id ) );

    return item;
  }

  public synchronized List<IServerItem> getItems( ) throws HydPyServerException
  {
    if( m_items == null )
    {
      m_items = requestItems();
      m_itemIndex = new HashMap<>( m_items.size() );

      for( final AbstractServerItem item : m_items )
        m_itemIndex.put( item.getId(), item );

      m_fixedItems = requestFixedItems();
    }

    final List< ? extends IServerItem> items = m_items;
    return Collections.unmodifiableList( items );
  }

  private List<AbstractServerItem> requestItems( ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_EXECUTE, "initializing", METHODS_GET_ITEMTYPES__OPENDA );

    final Properties props = callGetAndParse( endpoint, m_timeoutMillis );

    final List<AbstractServerItem> items = new ArrayList<>( props.size() );

    for( final String property : props.stringPropertyNames() )
    {
      final String value = props.getProperty( property );

      final AbstractServerItem item = AbstractServerItem.fromHydPyType( property, value );
      items.add( item );
    }

    items.add( AbstractServerItem.newTimeItem( ITEM_ID_FIRST_DATE ) );
    items.add( AbstractServerItem.newTimeItem( ITEM_ID_LAST_DATE ) );
    items.add( AbstractServerItem.newDurationItem( ITEM_ID_STEP_SIZE ) );

    return items;
  }

  private Map<String, Object> requestFixedItems( ) throws HydPyServerException
  {
    // REMARK: HydPy always need instanceId; we give fake one here
    final URI endpoint = buildEndpoint( PATH_EXECUTE, "initializing", METHODS_GET_ITEMVALUES__OPENDA ); //$NON-NLS-1$

    final Properties props = callGetAndParse( endpoint, m_timeoutMillis );

    /* pre-parse items */
    final Map<String, Object> values = preParseValues( props );

    /* only use those as 'fixed' that are really declared as those */
    values.keySet().retainAll( m_fixedParameters );

    return values;
  }

  public List<IPrevExchangeItem> getItemValues( final String instanceId ) throws HydPyServerException
  {
    // System.out.println( String.format( "Retrieving HydPy-Model State - InstanceId = '%s'", instanceId ) );

    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, METHODS_GET_ITEMVALUES__OPENDA );

    final Properties props = callGetAndParse( endpoint, m_timeoutMillis );

    /* pre-parse items */
    final Map<String, Object> preValues = preParseValues( props );
    preValues.putAll( m_fixedItems );

    /* fetch fixed items value necessary to parse timeseries */
    final Instant startTime = (Instant)preValues.get( ITEM_ID_FIRST_DATE );
    final Instant endTime = (Instant)preValues.get( ITEM_ID_LAST_DATE );
    final long stepSeconds = (Long)preValues.get( ITEM_ID_STEP_SIZE );

    /* parse item values */
    final List<IPrevExchangeItem> values = new ArrayList<>( preValues.size() );

    final Set<Entry<String, Object>> entrySet = preValues.entrySet();
    for( final Entry<String, Object> entry : entrySet )
    {
      final String id = entry.getKey();
      final AbstractServerItem item = getItem( id );

      final Object preValue = entry.getValue();

      final IPrevExchangeItem value = item.toExchangeItem( startTime, endTime, stepSeconds, preValue );
      values.add( value );

      // final String valueText = item.printValue( value, stepSeconds );
      // System.out.format( "%s = %s%n", value.getId(), valueText );
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
    // System.out.println( String.format( "Setting HydPy-Model State - InstanceId = '%s'", instanceId ) );

    final long stepSeconds = findStepSeconds( values );

    final StringBuffer body = new StringBuffer();
    for( final IPrevExchangeItem exItem : values )
    {
      final String id = exItem.getId();

      final AbstractServerItem item = getItem( id );
      final String valueText = item.printValue( exItem, stepSeconds );

      // System.out.format( "%s = %s%n", item.getId(), valueText );

      body.append( id );
      body.append( '=' );
      body.append( valueText );
      body.append( '\r' );
      body.append( '\n' );
    }

    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, METHODS_POST_ITEMVALUES__OPENDA );
    callPost( endpoint, m_timeoutMillis, body.toString() );
  }

  private long findStepSeconds( final Collection<IPrevExchangeItem> values )
  {
    for( final IPrevExchangeItem item : values )
    {
      if( ITEM_ID_STEP_SIZE.equals( item.getId() ) )
      {
        final double value = ((DoubleExchangeItem)item).getValue();
        return (long)value;
      }
    }

    throw new IllegalStateException( "Failed to find stepsize exchange item" );
  }

  boolean checkStatus( final int timeout ) throws HydPyServerException, HydPyServerProcessException
  {
    // REMARK: extra check process so we get the special exception
    checkProcess();

    final URI endpoint = buildEndpoint( PATH_STATUS, null, null );
    final HttpEntity entity = callGet( endpoint, timeout );

    try
    {
      final String responseContent = EntityUtils.toString( entity );

      return "status = ready".equalsIgnoreCase( responseContent );
    }
    catch( final Exception e )
    {
      throw new HydPyServerException( "Failed to read HydPy-Server response", e );
    }
  }

  public void simulate( final String instanceId ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, METHODS_GET_SIMULATE__OPENDA );
    callGet( endpoint, m_timeoutMillis );
  }

  void shutdown( ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_CLOSE_SERVER, null, null );
    callGet( endpoint, m_timeoutMillis );
  }
}