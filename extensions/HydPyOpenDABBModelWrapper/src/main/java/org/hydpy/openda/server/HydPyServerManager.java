/**
 * Copyright (c) 2019 by
 * - Bundesanstalt f�r Gew�sserkunde
 * - Bj�rnsen Beratende Ingenieure GmbH
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.http.client.utils.URIBuilder;
import org.hydpy.openda.HydPyUtils;

/**
 * Manages the life-cycle of the {@link HydPyServer}.
 * REMARK: at the moment, we support only one running server at the same time. However, this class is intended to support management of multiple servers at once.
 *
 * @author Gernot Belger
 */
public final class HydPyServerManager
{
  private static final String ENVIRONMENT_HYD_PY_EXE = "HYD_PY_EXE"; //$NON-NLS-1$

  private static final String ENVIRONMENT_HYD_PY_SCRIPT_PATH = "HYD_PY_SCRIPT_PATH"; //$NON-NLS-1$

  private static final String PROPERTY_SERVER_PORT = "serverPort"; //$NON-NLS-1$

  private static final String PROPERTY_INITIALIZE_SECONDS = "initializeWaitSeconds"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_PATH = "projectPath"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_NAME = "projectName"; //$NON-NLS-1$

  private static final String PROPERTY_CONFIG_FILE = "configFile"; //$NON-NLS-1$

  private static HydPyServerManager INSTANCE = null;

  public static synchronized void create( final Path baseDir, final Properties args )
  {
    INSTANCE = new HydPyServerManager( baseDir, args );
  }

  public synchronized static HydPyServerManager instance( )
  {
    if( INSTANCE == null )
      throw new IllegalStateException( "create was never called" );

    return INSTANCE;
  }

  private class ShutdownThread extends Thread
  {
    private final HydPyServerManager m_manager;

    public ShutdownThread( final HydPyServerManager manager )
    {
      super( "HydPyServer shutdown" );

      m_manager = manager;
    }

    @Override
    public void run( )
    {
      m_manager.killAllServers();
    }
  }

  private final String m_host;

  private final int m_port;

  private final String m_serverExe;

  private final int m_initRetrySeconds;

  private final String m_hydPyScript;

  private final String m_projectPath;

  private final String m_projectName;

  private HydPyServer m_server = null;

  private final String m_configFile;

  private HydPyServerManager( final Path baseDir, final Properties args )
  {
    // REMARK: always try to shutdown the running hyd
    Runtime.getRuntime().addShutdownHook( new ShutdownThread( this ) );

    // REAMRK: we open a local process, so this is always localhost (for now)
    m_host = "localhost";

    /* absolute paths from system environment */
    m_serverExe = HydPyUtils.getRequiredSystemProperty( ENVIRONMENT_HYD_PY_EXE );
    m_hydPyScript = HydPyUtils.getRequiredSystemProperty( ENVIRONMENT_HYD_PY_SCRIPT_PATH );

    /* Everything else from model.xml arguments */
    m_port = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_SERVER_PORT );
    m_initRetrySeconds = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_INITIALIZE_SECONDS );

    final String projectPath = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_PATH );
    m_projectPath = baseDir.resolve( projectPath ).normalize().toString();

    m_projectName = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_NAME );
    m_configFile = HydPyUtils.getRequiredProperty( args, PROPERTY_CONFIG_FILE );
  }

  public synchronized HydPyServer getOrCreateServer( )
  {
    if( m_server == null )
      m_server = createServer();

    return m_server;
  }

  private HydPyServer createServer( )
  {
    try
    {
      final URI address = new URIBuilder() //
          .setScheme( "http" ) //
          .setHost( m_host ) //
          .setPort( m_port ) //
          .build();

      /* start the real server */
      final Process process = startHydPyProcess();

      final HydPyServer server = new HydPyServer( address, process );

      tryCallServer( server );

      return server;
    }
    catch( final URISyntaxException | IOException | HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to start HydPy Server", e );
    }
  }

  private void tryCallServer( final HydPyServer server ) throws HydPyServerException
  {
    final int retries = m_initRetrySeconds * 4;
    final int timeoutMillis = 250;

    for( int i = 0; i < retries; i++ )
    {
      try
      {
        if( server.checkStatus( timeoutMillis ) )
          return;
      }
      catch( final HydPyServerException ignored )
      {
        System.out.println( "Waiting for HyPy-Server: " + i );
        /* continue waiting */
      }
      catch( final HydPyServerProcessException e )
      {
        /* the process was terminated, we will never get an answer now */
        e.printStackTrace();
        throw new HydPyServerException( e.getMessage() );
      }
    }

    server.killServer();

    final String message = String.format( "Timeout waiting for HydPy-Server after %d seconds", m_initRetrySeconds );
    throw new HydPyServerException( message );
  }

  private Process startHydPyProcess( ) throws IOException
  {
    final File projectDir = new File( m_projectPath );
    final File workingDir = projectDir;

    final String command = m_serverExe;
    final String operation = "start_server";
    final String port = Integer.toString( m_port );

    final ProcessBuilder builder = new ProcessBuilder( command, m_hydPyScript, operation, port, m_projectName, m_configFile );
    builder.directory( workingDir );
    builder.inheritIO();
    return builder.start();
  }

  protected void killAllServers( )
  {
    if( m_server == null )
      return;

    try
    {
      m_server.shutdown();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }
    m_server = null;
  }
}