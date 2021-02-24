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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.hydpy.openda.HydPyUtils;

/**
 * @author Gernot Belger
 */
public final class HydPyServerConfiguration
{
  private static final String ENVIRONMENT_HYD_PY_PYTHON_EXE = "HYD_PY_PYTHON_EXE"; //$NON-NLS-1$

  private static final String ENVIRONMENT_HYD_PY_SCRIPT_PATH = "HYD_PY_SCRIPT_PATH"; //$NON-NLS-1$

  private static final String PROPERTY_SERVER_PORT = "serverPort"; //$NON-NLS-1$

  private static final String PROPERTY_SERVER_MAX_PROCESSES = "serverInstances"; //$NON-NLS-1$

  private static final String PROPERTY_INITIALIZE_SECONDS = "initializeWaitSeconds"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_PATH = "projectPath"; //$NON-NLS-1$

  private static final String PROPERTY_PROJECT_NAME = "projectName"; //$NON-NLS-1$

  private static final String PROPERTY_CONFIG_FILE = "configFile"; //$NON-NLS-1$

  private static final String PROPERTY_LOG_DIRECTORY = "logDirectory"; //$NON-NLS-1$

  private static final String HYD_PY_PYTHON_EXE_DEFAULT = "python.exe"; //$NON-NLS-1$

  private static final String HYD_PY_SCRIPT_PATH_DEFAULT = "hy.py"; //$NON-NLS-1$

  public final Path workingDir;

  // REAMRK: we open a local process, so this is always localhost (for now)
  public final String host = "localhost";

  public final String serverExe;

  public final String hydPyScript;

  public final int startPort;

  public final int maxProcesses;

  public final int initRetrySeconds;

  public final Path modelDir;

  public final String modelName;

  public final Path configFile;

  public final Path logDirectory;

  public HydPyServerConfiguration( final Path workDir, final Properties args )
  {
    workingDir = workDir;

    /* absolute paths from system environment */
    serverExe = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_PYTHON_EXE, HYD_PY_PYTHON_EXE_DEFAULT );
    hydPyScript = HydPyUtils.getOptionalSystemProperty( ENVIRONMENT_HYD_PY_SCRIPT_PATH, HYD_PY_SCRIPT_PATH_DEFAULT );

    /* Everything else from model.xml arguments */
    startPort = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_SERVER_PORT );

    maxProcesses = HydPyUtils.getOptionalPropertyAsInt( args, PROPERTY_SERVER_MAX_PROCESSES, 1 );
    if( maxProcesses < 1 )
      throw new RuntimeException( String.format( "Argument '%s': must be positive", PROPERTY_SERVER_MAX_PROCESSES ) );

    if( startPort + maxProcesses - 1 > 0xFFFF )
      throw new RuntimeException( String.format( "Arguments '%s'+'%s': exceeds maximal possible port 0xFFFF", PROPERTY_SERVER_PORT, PROPERTY_SERVER_MAX_PROCESSES ) );

    initRetrySeconds = HydPyUtils.getRequiredPropertyAsInt( args, PROPERTY_INITIALIZE_SECONDS );

    final String projectDirArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_PATH );
    modelDir = workingDir.resolve( projectDirArgument ).normalize();
    if( !Files.isDirectory( modelDir ) )
      throw new RuntimeException( String.format( "Argument '%s': Directory does not exist: %s", PROPERTY_PROJECT_PATH, modelDir ) );

    modelName = HydPyUtils.getRequiredProperty( args, PROPERTY_PROJECT_NAME );

    final String configFileArgument = HydPyUtils.getRequiredProperty( args, PROPERTY_CONFIG_FILE );
    configFile = workingDir.resolve( configFileArgument ).normalize();
    if( !Files.isRegularFile( configFile ) )
      throw new RuntimeException( String.format( "Argument '%s': File does not exist: %s", PROPERTY_CONFIG_FILE, configFile ) );

    final String logDirectoryArgument = args.getProperty( PROPERTY_LOG_DIRECTORY, null );
    logDirectory = logDirectoryArgument == null ? null : workingDir.resolve( logDirectoryArgument ).normalize();
  }
}