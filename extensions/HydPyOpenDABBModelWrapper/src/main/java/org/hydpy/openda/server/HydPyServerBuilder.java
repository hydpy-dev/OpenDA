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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;

/**
 * @author Gernot Belger
 */
final class HydPyServerBuilder
{
  private final String m_host;

  private final int m_startPort;

  private final String m_serverExe;

  private final String m_hydPyScript;

  private final Path m_workingDir;

  private final String m_projectName;

  private final Path m_configFile;

  private final Path m_logDirectory;

  private final int m_initRetrySeconds;

  public HydPyServerBuilder( final int startPort, final String serverExe, final String hydPyScript, final Path projectDir, final String projectName, final Path logDirectory, final Path configFile, final int initRetrySeconds )
  {
    // REAMRK: we open a local process, so this is always localhost (for now)
    m_host = "localhost";
    m_startPort = startPort;
    m_serverExe = serverExe;
    m_hydPyScript = hydPyScript;
    m_workingDir = projectDir;
    m_projectName = projectName;
    m_logDirectory = logDirectory;
    m_configFile = configFile;
    m_initRetrySeconds = initRetrySeconds;
  }

  public HydPyServerProcess start( final int processId ) throws HydPyServerException
  {
    final int port = m_startPort + processId;

    final URI address = createAddress( port );

    final String name = String.format( "HydPyServer %d - %s", processId, port );

    final Process process = startProcess( port, processId );

    final HydPyServerProcess server = new HydPyServerProcess( name, address, process );

    try
    {
      tryCallServer( server );

      return server;
    }
    catch( final HydPyServerException e )
    {
      /* if the test-call fails, we directly destroy the process, the manager can't do it */
      process.destroy();

      throw e;
    }
  }

  private URI createAddress( final int port ) throws HydPyServerException
  {
    try
    {
      return new URIBuilder() //
          .setScheme( "http" ) //
          .setHost( m_host ) //
          .setPort( port ) //
          .build();
    }
    catch( final URISyntaxException e )
    {
      /* should never happen */
      throw new HydPyServerException( "Failed to build address", e );
    }
  }

  private Process startProcess( final int port, final int processId ) throws HydPyServerException
  {
    try
    {
      final String command = m_serverExe;
      final String operation = "start_server";
      final String portArgument = Integer.toString( port );
      final String configFile = m_configFile.toString();

      final ProcessBuilder builder = new ProcessBuilder( command, m_hydPyScript, operation, portArgument, m_projectName, configFile ) //
          .directory( m_workingDir.toFile() );

      if( m_logDirectory == null )
        builder.inheritIO();
      else
      {
        final File logFile = new File( m_logDirectory.toFile(), String.format( "HydPy_Server_%d.log", processId ) );
        final File errFile = new File( m_logDirectory.toFile(), String.format( "HydPy_Server_%d.err", processId ) );
        builder.redirectError( errFile );
        builder.redirectOutput( logFile );
      }

      System.out.format( "Starting HydPy-Server %d...%n", processId );
      final List<String> commandLine = builder.command();
      System.out.println( "Working-Directory:" );
      System.out.println( m_workingDir );
      System.out.println( "Command-Line:" );
      for( final String commmandPart : commandLine )
      {
        System.out.print( commmandPart );
        System.out.print( ' ' );
      }
      System.out.println();

      return builder.start();
    }
    catch( final IOException e )
    {
      throw new HydPyServerException( "Failed to start HydPy-Process", e );
    }
  }

  private void tryCallServer( final HydPyServerProcess server ) throws HydPyServerException
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
        // TODO: use logging framework?
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

    final String message = String.format( "Timeout waiting for HydPy-Server after %d seconds", m_initRetrySeconds );
    throw new HydPyServerException( message );
  }
}