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

import java.io.PrintStream;
import java.io.StringReader;
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
import org.apache.http.util.EntityUtils;

/**
 * Represents a client calling a HydPy server process which handles the basic http calls.
 *
 * @author Gernot Belger
 */
final class HydPyServerClient
{
  public final class Poster
  {
    private final StringBuffer m_body = new StringBuffer();

    private final String m_instanceId;

    private final String m_methods;

    public Poster( final String instanceId, final String methods )
    {
      m_instanceId = instanceId;
      m_methods = methods;
    }

    public Poster body( final String key, final String value )
    {
      m_body.append( key ).append( '=' ).append( value ).append( '\r' ).append( '\n' );
      return this;
    }

    public Properties execute( ) throws HydPyServerException
    {
      return HydPyServerClient.this.callPost( m_instanceId, m_methods, m_body.toString() );
    }
  }

  private static final String PATH_STATUS = "status"; //$NON-NLS-1$

  private static final String PATH_VERSION = "version"; //$NON-NLS-1$

  private static final String PATH_EXECUTE = "execute"; //$NON-NLS-1$

  private static final String PATH_CLOSE_SERVER = "close_server"; //$NON-NLS-1$

  private static final String PARAMETER_INSTANCE_NUMBER = "id"; //$NON-NLS-1$

  private static final String PARAMETER_METHODS = "methods"; //$NON-NLS-1$

  private final URI m_address;

  // FIXME: configure??
  private final int m_timeoutMillis = 60000;

  private final PrintStream m_debugOut;

  public HydPyServerClient( final URI address, final PrintStream debugOut )
  {
    m_address = address;
    m_debugOut = debugOut;
  }

  private HttpEntity callGet( final URI endpoint, final int timeout ) throws HydPyServerException
  {
    m_debugOut.println( "Calling GET:" );
    m_debugOut.println( endpoint );

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

      final String content = EntityUtils.toString( entity );

      m_debugOut.println( "Received from HydPy Server:" );
      m_debugOut.println( content );

      final StringReader reader = new StringReader( content );

      props.load( reader );
      return props;
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
    m_debugOut.println( "Calling POST:" );
    m_debugOut.println( endpoint );
    m_debugOut.println( body );

    final ContentType contentType = ContentType.TEXT_PLAIN.withCharset( StandardCharsets.UTF_8 );

    final Request request = Request.Post( endpoint ).bodyString( body, contentType );

    return callServer( request, timeout );
  }

  private HttpEntity callServer( final Request request, final int timeout ) throws HydPyServerException
  {
    try
    {
      final HttpResponse response = request //
          .connectTimeout( timeout ) //
          .socketTimeout( timeout ) //
          .execute()//
          .returnResponse();

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
  public synchronized Properties callGet( final String instanceId, final String methods ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_EXECUTE, instanceId, methods );
    return callGetAndParse( endpoint, m_timeoutMillis );
  }

  protected synchronized Properties callPost( final String instanceId, final String methods, final String postBody ) throws HydPyServerException
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

  public Version getVersion( final int timeout ) throws HydPyServerException
  {
    final URI endpoint = buildEndpoint( PATH_VERSION, null, null );
    final Properties response = callGetAndParse( endpoint, timeout );
    return Version.parse( response.getProperty( "version" ) );
  }

  public void shutdown( )
  {
    try
    {
      final URI endpoint = buildEndpoint( PATH_CLOSE_SERVER, null, null );
      callGet( endpoint, m_timeoutMillis );
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }
  }

  public void debugOut( final String name, final String message, final Object... arguments )
  {
    m_debugOut.print( name );
    m_debugOut.print( ": " ); //$NON-NLS-1$
    m_debugOut.format( message, arguments );
    m_debugOut.println();
  }

  public Poster callPost( final String instanceId, final String methods )
  {
    return new Poster( instanceId, methods );
  }
}