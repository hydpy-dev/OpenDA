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
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

/**
 * Represents a client calling a HydPy server process which handles the basic http calls.
 *
 * @author Gernot Belger
 */
final class HydPyServerClient
{
  private static final String PATH_STATUS = "get_status"; //$NON-NLS-1$

  private static final String PATH_VERSION = "get_version"; //$NON-NLS-1$

  private static final String PATH_EXECUTE = "execute"; //$NON-NLS-1$

  private static final String PATH_CLOSE_SERVER = "close_server"; //$NON-NLS-1$

  private static final String PARAMETER_INSTANCE_NUMBER = "id"; //$NON-NLS-1$

  private static final String PARAMETER_METHODS = "methods"; //$NON-NLS-1$

  private final URI m_address;

  private final int m_timeoutMillis = 60000;

  public HydPyServerClient( final URI address )
  {
    m_address = address;
  }

  private HttpEntity callGet( final URI endpoint, final int timeout ) throws HydPyServerException
  {
    final Request request = Request.Get( endpoint );
    return callServer( request, timeout );
  }

  private Properties callGetAndParse( final URI endpoint, final int timeoutMillis ) throws HydPyServerException
  {
    final HttpEntity entity = callGet( endpoint, timeoutMillis );

    return parseAsProperties( entity );
  }

  private Properties parseAsProperties( final HttpEntity entity ) throws HydPyServerException
  {
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

  private Properties callPostAndParse( final URI endpoint, final int timeout, final String body ) throws HydPyServerException
  {
    final HttpEntity response = callPost( endpoint, timeout, body );
    return parseAsProperties( response );
  }

  private HttpEntity callPost( final URI endpoint, final int timeout, final String body ) throws HydPyServerException
  {
    final ContentType contentType = ContentType.TEXT_PLAIN.withCharset( StandardCharsets.UTF_8 );

    final Request request = Request.Post( endpoint ).bodyString( body, contentType );

    return callServer( request, timeout );
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
    catch( final HydPyServerException e )
    {
      throw e;
    }
    catch( final Exception e )
    {
      throw new HydPyServerException( "Failed to connect to HydPy-Server", e );
    }
  }

  // REMARK: synchronized execute in order to block sequential calls to the same server.
  // Normally this should already happen via the server-side (using non-threaded HttpServer),
  // however we still get sometimes 'Connection Refused' errors if too many calls are made within a small timespan.
  // Enlarging the socket-queue-size does not really help.
  public synchronized Properties execute( final String instanceId, final String methods ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, methods );
    return callGetAndParse( endpoint, m_timeoutMillis );
  }

  public synchronized Properties execute( final String instanceId, final String methods, final String postBody ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, methods );
    return callPostAndParse( endpoint, m_timeoutMillis, postBody );
  }

  public boolean checkStatus( final int timeout ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_STATUS, null, null );
    final Properties response = callGetAndParse( endpoint, timeout );

    final String status = response.getProperty( "status" );
    return "ready".equalsIgnoreCase( status );
  }

  public HydPyVersion getVersion( final int timeout ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_VERSION, null, null );
    final Properties response = callGetAndParse( endpoint, timeout );
    return HydPyVersion.parse( response.getProperty( "version" ) );
  }

  public void shutdown( )
  {
    try
    {
      final URI endpoint = buildEndpoint( PATH_CLOSE_SERVER, null, null );
      callGet( endpoint, m_timeoutMillis );
      // TODO: check if process was correctly terminated, needs bigger java version
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }
  }
}