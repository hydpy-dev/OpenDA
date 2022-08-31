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
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.io.output.NullPrintStream;
import org.apache.http.client.utils.URIBuilder;
import org.hydpy.openda.HydPyInstanceConfiguration;
import org.hydpy.openda.server.HydPyServerConfiguration.LogMode;

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

  private final Future<HydPyServerInstance> m_future;

  private Process m_process = null;

  // REMARK: initialize with System.out so we do not need to worry about NPE.
  private PrintStream m_debugOut = System.out;

  private PrintStream m_debugOutToClose = null;

  private final HydPyInstanceConfiguration m_instanceDirs;

  public HydPyServerStarter( final HydPyServerConfiguration config, final HydPyInstanceConfiguration instanceDirs, final int processId )
  {
    m_config = config;
    m_instanceDirs = instanceDirs;
    m_processId = processId;

    m_port = config.startPort + processId;
    m_name = String.format( "HydPyServer %d - %s", processId, m_port );

    final ThreadFactory threadFactory = new HydPyThreadFactory( m_name );
    m_executor = Executors.newSingleThreadExecutor( threadFactory );

    m_future = HydPyUtils.submitAndLogExceptions( m_executor, this::doStart );
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

    m_debugOut = createDebugOut();

    m_process = startProcess( m_debugOut );

    final HydPyServerClient client = new HydPyServerClient( address, m_debugOut, m_config.timeout );

    try
    {
      tryCallServer( client, m_debugOut );

      final long end = System.currentTimeMillis();
      final double time = (end - start) / 1000.0;
      m_debugOut.format( "%s: ready after %.2f seconds%n", m_name, time );

      /* wrap for OpenDA specific calling */
      final HydPyOpenDACaller openDaCaller = new HydPyOpenDACaller( m_name, client );

      /* return the real implementation which is always threaded per process */
      return new HydPyServerInstance( openDaCaller, m_executor );
    }
    catch( final HydPyServerException e )
    {
      /* if the test-call fails, we directly destroy the process, the manager can't do it */
      if( m_process != null )
      {
        m_process.destroyForcibly();
        m_process = null;
      }

//      /* we also shutdown the thread, else OpenDA might hang forever */
//      m_executor.shutdown();

      throw e;
    }
  }

  private PrintStream createDebugOut( )
  {
    final LogMode logMode = m_config.logMode;
    switch( logMode )
    {
      case off:
        /* print into the void */
        return new NullPrintStream();

      case console:
        return System.out;

      case file:
      {
        try
        {
          final Path logDir = m_config.logDirectory;
          final String filename = String.format( "HydPy_Client_%d.log", m_processId );

          final File file = new File( logDir.toFile(), filename );
          // REMARK: default charset should be ok, this is for debugging only
          m_debugOutToClose = new PrintStream( file, Charset.defaultCharset().name() );
          return m_debugOutToClose;
        }
        catch( final IOException e )
        {
          e.printStackTrace();
          System.err.println( "Failed to create logfile, falling back to console output" );
          return System.out;
        }
      }

      default:
        throw new IllegalStateException();
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

  private Process startProcess( final PrintStream debugOut ) throws HydPyServerException
  {
    try
    {
      if( m_config.preStarted )
      {
        debugOut.format( "%s: assuming process is already started ...%n", m_name );
        return null;
      }

      final String command = m_config.serverExe;
      final String operation = "start_server";
      final String portArgument = Integer.toString( m_port );
      final String configFile = m_config.configFile.toString();

      // REMARK: let hydpy load conditions/series only if it's not triggered via the wrapper.
      final boolean doHydPyLoadConditions = !m_instanceDirs.isLoadConditions();
      final boolean doHdPyLoadSeries = !m_instanceDirs.isLoadSeries();
      final String loadConditionsParam = "load_conditions=" + doHydPyLoadConditions;
      final String loadSeriesParam = "load_series=" + doHdPyLoadSeries;

      final String[] parameters = new String[] { command, //
          m_config.hydPyScript, //
          operation, //
          portArgument, //
          m_config.modelName, //
          configFile, //
          loadConditionsParam, //
          loadSeriesParam };

      final ProcessBuilder builder = new ProcessBuilder( parameters ) //
          .directory( m_config.modelDir.toFile() );

      configureProcessForLogging( builder );

      debugOut.format( "%s: starting ...%n", m_name );
      final List<String> commandLine = builder.command();
      debugOut.println( "Working-Directory:" );
      debugOut.println( m_config.modelDir );
      debugOut.println( "Command-Line:" );
      for( final String commmandPart : commandLine )
      {
        debugOut.print( commmandPart );
        debugOut.print( ' ' );
      }
      debugOut.println();

      return builder.start();
    }
    catch( final IOException e )
    {
      throw new HydPyServerException( String.format( "%s: failed to start process", m_name ), e );
    }
  }

  private void configureProcessForLogging( final ProcessBuilder builder )
  {
    switch( m_config.logMode )
    {
      case off:
        // FIXME: as soon as OpenDA goes to Java >9; use Redirect.DISCARD instead and remove
        // the pipe stream which is later constructed
        // builder.redirectError( Redirect.DISCARD );
        // builder.redirectOutput( Redirect.DISCARD );
        // Simulate the Java 9 behavior
        final File NULL_FILE = new File( System.getProperty( "os.name" ).startsWith( "Windows" ) ? "NUL" : "/dev/null" );
        builder.redirectError( NULL_FILE );
        builder.redirectOutput( NULL_FILE );

        return;

      case console:
        builder.inheritIO();
        return;

      case file:
      {
        final File logFile = new File( m_config.logDirectory.toFile(), String.format( "HydPy_Server_%d.log", m_processId ) );
        final File errFile = new File( m_config.logDirectory.toFile(), String.format( "HydPy_Server_%d.err", m_processId ) );
        builder.redirectError( errFile );
        builder.redirectOutput( logFile );
        return;
      }

      default:
        throw new IllegalStateException();
    }
  }

  private void tryCallServer( final HydPyServerClient client, final PrintStream debugOut ) throws HydPyServerException
  {
    final int retries = m_config.initRetrySeconds * 4;
    final int timeoutMillis = 250;

    for( int i = 0; i < retries; i++ )
    {
      final long startTime = System.currentTimeMillis();

      try
      {
        if( m_process == null )
        {
          /* this happen if preStarted is set to true, we 'simulate' a running process an try to call getVersion */
          throw new IllegalThreadStateException();
        }

        m_process.exitValue();
        throw new HydPyServerException( String.format( "%s: unexpectedly terminated", m_name ) );
      }
      catch( final IllegalThreadStateException e )
      {
        try
        {
          // Process is still living, continue
          final Version version = client.getVersion( timeoutMillis );
          HydPyRequirements.checkHydPyVersion( version, debugOut );
          return;
        }
        catch( final HydPyServerException ex )
        {
          debugOut.format( "%s: waiting for startup...%n", m_name );
          debugOut.format( "%s: %s%n", m_name, ex.getLocalizedMessage() );

          if( i == retries - 1 )
            ex.printStackTrace();

          /* continue waiting */
          try
          {
            // REMARK: make sure we wait 250ms in any case, because the first calls to HydPy might return immediately
            // It then might happen, that we dont wait the full time and skip the check too early, before HydPy was really started.
            final long waitTime = System.currentTimeMillis() - startTime;
            if( waitTime < 250 )
              Thread.sleep( 250 );
          }
          catch( final InterruptedException e1 )
          {
            e1.printStackTrace();
          }
        }
      }
    }

    final String message = String.format( "%s: timeout after %d seconds", m_name, m_config.initRetrySeconds );
    throw new HydPyServerException( message );
  }

  public Future<Void> shutdown( )
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

    final Future<Void> future = HydPyUtils.submitAndLogExceptions( m_executor, this::shutdownProcessAndCloseDebugOut );

    m_executor.shutdown();

    return future;
  }

  private void shutdownProcessAndCloseDebugOut( )
  {
    shutdownProcess();

    if( m_debugOutToClose != null )
    {
      try
      {
        m_debugOutToClose.close();
        m_debugOutToClose = null;
      }
      catch( final Exception e )
      {
        e.printStackTrace();
      }
    }
  }

  private void shutdownProcess( )
  {
    if( m_process == null )
      return;

    final int timeout = 2000;
    final int waitTime = 50;
    for( int i = 0; i < timeout; i += waitTime )
    {
      try
      {
        m_process.exitValue();

        /* process has terminated, stop waiting for it */
        m_debugOut.format( "%s: process terminated%n", m_name );
        return;
      }
      catch( final IllegalThreadStateException e )
      {
        try
        {
          /* process was not yet terminated, wait for it */
          m_debugOut.format( "%s: waiting for process termination...%n", m_name );
          Thread.sleep( waitTime );
        }
        catch( final InterruptedException e1 )
        {
          e1.printStackTrace();
        }
      }
    }

    /* process was not correctly terminated, kill */
    m_debugOut.format( "%s: timeout waiting for process termination, process will be killed%n", m_name );
    m_process.destroy();
  }

  public void kill( )
  {
    if( m_process == null )
      return;

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