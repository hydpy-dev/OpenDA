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
  private final static HydPyVersion VERSION_SUPPORTED = new HydPyVersion( 4, 1, 0, true );

  private final ExecutorService m_executor;

  private final HydPyServerConfiguration m_config;

  private final int m_processId;

  private final int m_port;

  private final String m_name;

  private final Future<HydPyServerInstance> m_future;

  private Process m_process = null;

  public HydPyServerStarter( final HydPyServerConfiguration config, final int processId )
  {
    m_config = config;
    m_processId = processId;

    m_port = config.startPort + processId;
    m_name = String.format( "HydPyServer %d - %s", processId, m_port );

    final ThreadFactory threadFactory = new HydPyThreadFactory( m_name );
    m_executor = Executors.newSingleThreadExecutor( threadFactory );

    final Callable<HydPyServerInstance> task = new Callable<HydPyServerInstance>()
    {
      @Override
      public HydPyServerInstance call( ) throws Exception
      {
        return doStart();
      }
    };

    m_future = m_executor.submit( task );
  }

  /**
   * Get the startet server process instance. Blocks until it is really started.
   */
  public HydPyServerInstance getServer( ) throws HydPyServerException
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

  protected HydPyServerInstance doStart( ) throws HydPyServerException
  {
    final URI address = createAddress();

    final long start = System.currentTimeMillis();

    m_process = startProcess();

    final HydPyServerClient client = new HydPyServerClient( address );

    try
    {
      tryCallServer( client );

      final long end = System.currentTimeMillis();
      final double time = (end - start) / 1000.0;
      System.out.format( "%s: ready after %.2f seconds%n", m_name, time );

      /* wrap for OpenDA specific calling */
      final HydPyOpenDACaller openDaCaller = new HydPyOpenDACaller( m_name, client );

      /* return the real implementation which is always threaded per process */
      return new HydPyServerInstance( openDaCaller, m_executor );
    }
    catch( final HydPyServerException e )
    {
      /* if the test-call fails, we directly destroy the process, the manager can't do it */
      m_process.destroy();

      /* we also shutdown the thread, else OpenDA might hang forever */
      shutdownExecutor();

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

      System.out.format( "%s: starting ...%n", m_name );
      final List<String> commandLine = builder.command();
      System.out.println( "Working-Directory:" );
      System.out.println( m_config.modelDir );
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
      throw new HydPyServerException( String.format( "%s: failed to start process", m_name ), e );
    }
  }

  private void tryCallServer( final HydPyServerClient client ) throws HydPyServerException
  {
    final int retries = m_config.initRetrySeconds * 4;
    final int timeoutMillis = 250;

    for( int i = 0; i < retries; i++ )
    {
      try
      {
        m_process.exitValue();
        throw new HydPyServerException( String.format( "%s: unexpectedly terminated", m_name ) );
      }
      catch( final IllegalThreadStateException e )
      {
        try
        {
          // Process is still living, continue
          final HydPyVersion version = client.getVersion( timeoutMillis );

          if( version.compareTo( VERSION_SUPPORTED ) < 0 )
            System.err.format( "WARNING: HydPy Version of Server (%s) is LESS than the supported vrsion (%s) of this wrapper, do expect compatibility problems.%n", version, VERSION_SUPPORTED );
          else if( version.compareTo( VERSION_SUPPORTED ) > 0 )
            System.out.format( "INFO: HydPy Version of Server (%s) is GREATER than the supported vrsion (%s) of this wrapper%n", version, VERSION_SUPPORTED );
          return;
        }
        catch( final HydPyServerException ex )
        {
          System.out.format( "%s: waiting for startup...%n", m_name );
          System.out.format( "%s: %s%n", m_name, ex.getLocalizedMessage() );

          if( i == retries - 1 )
            ex.printStackTrace();

          /* continue waiting */
        }
      }
    }

    final String message = String.format( "%s: timeout after %d seconds", m_name, m_config.initRetrySeconds );
    throw new HydPyServerException( message );
  }

  public void shutdown( )
  {
    try
    {
      final HydPyServerInstance server = getServer();
      server.shutdown();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
    }

    shutdownExecutor();
  }

  private void shutdownExecutor( )
  {
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
      m_process.exitValue();

      /* process has already correctly terminated, nothing else to do */
    }
    catch( final IllegalThreadStateException e )
    {
      /* process was not correctly terminated, kill */
      System.err.format( "%s: killing service%n", m_name );
      m_process.destroy();
    }
  }
}