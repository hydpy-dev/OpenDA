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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.utils.URIBuilder;

/**
 * @author Gernot Belger
 */
final class HydPyServerStarter
{
  private final ExecutorService m_executor;

  private final HydPyServerConfiguration m_config;

  private final int m_processId;

  private final int m_port;

  private final String m_name;

  private final Future<IHydPyServerProcess> m_future;

  private final Collection<String> m_fixedParameters;

  public HydPyServerStarter( final HydPyServerConfiguration config, final Collection<String> fixedParameters, final int processId )
  {
    m_config = config;
    m_fixedParameters = fixedParameters;
    m_processId = processId;

    m_port = config.startPort + processId;
    m_name = String.format( "HydPyServer %d - %s", processId, m_port );

    final ThreadFactory threadFactory = new HydPyThreadFactory( m_name );
    m_executor = Executors.newSingleThreadExecutor( threadFactory );

    final Callable<IHydPyServerProcess> task = new Callable<IHydPyServerProcess>()
    {
      @Override
      public IHydPyServerProcess call( ) throws Exception
      {
        return doStart();
      }
    };

    m_future = m_executor.submit( task );
  }

  /**
   * Get the startet server process instance. Blocks until it is really started.
   */
  public IHydPyServerProcess getServer( ) throws HydPyServerException
  {
    try
    {
      return m_future.get();
    }
    catch( final Exception e )
    {
      throw toHydPyServerException( e );
    }
  }

  private HydPyServerException toHydPyServerException( final Exception e )
  {
    final Throwable cause = e.getCause();
    if( cause instanceof HydPyServerException )
      return (HydPyServerException)cause;

    return new HydPyServerException( cause );
  }

  protected IHydPyServerProcess doStart( ) throws HydPyServerException
  {
    final URI address = createAddress();

    final long start = System.currentTimeMillis();

    final Process process = startProcess();

    final HydPyServerProcess serverProcess = new HydPyServerProcess( m_name, address, process );

    try
    {
      tryCallServer( serverProcess );

      final long end = System.currentTimeMillis();
      final double time = (end - start) / 1000.0;
      System.out.format( "HydPy Server ready after %.2f seconds%n", time );

      /* wrap for OpenDA specific calling */
      final HydPyServerOpenDA server = new HydPyServerOpenDA( serverProcess, m_fixedParameters );

      /* return the real implementation which is always threaded per process */
      return new HydPyServerThreadingProcess( server, m_executor );
    }
    catch( final HydPyServerException e )
    {
      /* if the test-call fails, we directly destroy the process, the manager can't do it */
      process.destroy();

      throw e;
    }
  }

  private URI createAddress( ) throws HydPyServerException
  {
    try
    {
      return new URIBuilder() //
          .setScheme( "http" ) //
          .setHost( m_config.host ) //
          .setPort( m_port ) //
          .build();
    }
    catch( final URISyntaxException e )
    {
      /* should never happen */
      throw new HydPyServerException( "Failed to build address", e );
    }
  }

  private Process startProcess( ) throws HydPyServerException
  {
    try
    {
      final String command = m_config.serverExe;
      final String operation = "start_server";
      final String portArgument = Integer.toString( m_port );
      final String configFile = m_config.configFile.toString();

      final ProcessBuilder builder = new ProcessBuilder( command, m_config.hydPyScript, operation, portArgument, m_config.modelName, configFile ) //
          .directory( m_config.modelDir.toFile() );

      if( m_config.logDirectory == null )
        builder.inheritIO();
      else
      {
        final File logFile = new File( m_config.logDirectory.toFile(), String.format( "HydPy_Server_%d.log", m_processId ) );
        final File errFile = new File( m_config.logDirectory.toFile(), String.format( "HydPy_Server_%d.err", m_processId ) );
        builder.redirectError( errFile );
        builder.redirectOutput( logFile );
      }

      System.out.format( "Starting HydPy-Server %d...%n", m_processId );
      final List<String> commandLine = builder.command();
      System.out.println( "Working-Directory:" );
      System.out.println( m_config.workingDir );
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
    final int retries = m_config.initRetrySeconds * 4;
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
        System.out.format( "Waiting for %s...%n", m_name );
        /* continue waiting */
      }
      catch( final HydPyServerProcessException e )
      {
        /* the process was terminated, we will never get an answer now */
        e.printStackTrace();
        throw new HydPyServerException( e.getMessage() );
      }
    }

    final String message = String.format( "Timeout waiting for %s after %d seconds", m_name, m_config.initRetrySeconds );
    throw new HydPyServerException( message );
  }

  public void shutdown( )
  {
    try
    {
      final IHydPyServerProcess serverProcess = getServer();
      serverProcess.shutdown();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }

    try
    {
      /* shutdown executor after calls to processes, as they will submit a task on shutdown */
      m_executor.shutdown();
      m_executor.awaitTermination( 5, TimeUnit.SECONDS );
      System.out.format( "%s: shut down terminated%n", m_name );
    }
    catch( final InterruptedException e )
    {
      e.printStackTrace();
    }
  }

  public void kill( )
  {
    try
    {
      final IHydPyServerProcess serverProcess = getServer();
      serverProcess.kill();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }
  }
}