/**
 * Copyright (c) 2022 by
 * - Bundesanstalt für Gewässerkunde
 * - Björnsen Beratende Ingenieure GmbH
 * All rights reserved.
 *
 * This file is Free Software under the under the terms of the
 * GNU Lesser General Public License (LGPL >=v3)
 * and comes with ABSOLUTELY NO WARRANTY! Check out the
 * documentation coming with HydPy for details.
 */
package org.hydpy.openda;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.hydpy.openda.server.FileDeletionThread;
import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerManager;
import org.hydpy.openda.server.HydPyUtils;
import org.openda.blackbox.config.BBModelConfig;
import org.openda.blackbox.wrapper.BBModelInstance;
import org.openda.interfaces.IModelState;
import org.openda.interfaces.ITime;

/**
 * @author Gernot Belger
 */
final class HydPyBBModelInstance extends BBModelInstance
{
  public HydPyBBModelInstance( final BBModelConfig modelConfig, final int instanceNumber, final ITime timeHorizon )
  {
    super( modelConfig, instanceNumber, timeHorizon );
  }

  private Path getInternalStateFile( )
  {
    return getModelRunDir().toPath().resolve( HydPyModelFactory.PATH_HYDPY_INTERNAL_STATE );
  }

  @Override
  public IModelState saveInternalState( )
  {
    final String instanceId = getInstanceId();
    try
    {
      final Path tempDir = Files.createTempDirectory( "hydpyinternalstate_saving" );

      final HydPyModelInstance instance = HydPyServerManager.instance().getOrCreateInstance( instanceId, getModelRunDir() );

      /* let hydpy write its conditions */
      instance.writeConditions( tempDir.toFile() );

      /* zip/move to the real state file */
      final Path stateConditionsFile = getInternalStateFile();
      zipConditions( tempDir, stateConditionsFile );

      /* delete temp dir/file */
      // REMARK: hydpy deletes the directory if it writes a zip file...
      if( Files.isDirectory( tempDir ) )
        FileDeletionThread.instance().addFilesForDeletion( Collections.singletonList( tempDir.toFile() ) );
    }
    catch( final Exception e )
    {
      throw new RuntimeException( "Failed to write internal state", e );
    }

    return super.saveInternalState();
  }

  private void zipConditions( final Path sourceDir, final Path targetZipFile ) throws IOException
  {
    /* hydpy may be configured to create a zip file itself, use it directly if present */
    final String zipfilename = sourceDir.getFileName() + ".zip";
    final Path tipFile = sourceDir.getParent().resolve( zipfilename );
    if( Files.isRegularFile( tipFile ) )
      Files.move( tipFile, targetZipFile, StandardCopyOption.REPLACE_EXISTING );
    else
      HydPyUtils.zipConditionsDirectory( sourceDir, targetZipFile );
  }

  @Override
  public void restoreInternalState( final IModelState savedInternalState )
  {
    super.restoreInternalState( savedInternalState );

    final String instanceId = getInstanceId();

    try
    {
      /* unzip to a temp dir */
      // REMARK: currently (and hopefully this will be removed) hydpy is able to unzip itself, but will
      // delete the zip file in this case. We do not want this...
      final Path tempDir = Files.createTempDirectory( "hydpyinternalstate_loading" );
      final Path internalStateFile = getInternalStateFile();
      HydPyUtils.unzipConditions( internalStateFile, tempDir );

      // REMARK: we now tell HydPy to load the previously saved conditions file and register it for
      // the given instance
      final HydPyModelInstance instance = HydPyServerManager.instance().getOrCreateInstance( instanceId, getModelRunDir() );
      // REMARK: we let the instance delete it's files, because we do not want to block and hydpy may stil acess the files
      instance.restoreInternalState( tempDir.toFile(), true );
    }
    catch( final Exception e )
    {
      throw new RuntimeException( "Failed to read internal state", e );
    }
  }

  private String getInstanceId( )
  {
    try
    {
      final Field declaredField = BBModelInstance.class.getDeclaredField( "instanceNumberString" );
      declaredField.setAccessible( true );
      return (String)declaredField.get( this );
    }
    catch( final Exception e )
    {
      /* OpenDA style error handling */
      throw new RuntimeException( e );
    }
  }
}