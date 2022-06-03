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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerManager;
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
        FileUtils.forceDelete( tempDir.toFile() );
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
    {
      // REMARK: we know that hydpy only ever writes a flat list of files
      final List<Path> files = Files.list( sourceDir ).collect( Collectors.toList() );

      try( final ArchiveOutputStream o = new ZipArchiveOutputStream( Files.newOutputStream( targetZipFile ) ) )
      {
        for( final Path sourceFile : files )
        {
          if( !Files.isRegularFile( sourceFile ) )
            throw new IllegalStateException();

          // maybe skip directories for formats like AR that don't store directories
          final ArchiveEntry entry = o.createArchiveEntry( sourceFile, sourceFile.getFileName().toString() );
          o.putArchiveEntry( entry );
          try( final InputStream i = new BufferedInputStream( Files.newInputStream( sourceFile ) ) )
          {
            IOUtils.copy( i, o );
          }
          o.closeArchiveEntry();
        }
      }
    }
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
      unzipConditions( internalStateFile, tempDir );

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

  private void unzipConditions( final Path internalStateFile, final Path targetDir ) throws IOException
  {
    try( final ArchiveInputStream i = new ZipArchiveInputStream( Files.newInputStream( internalStateFile ) ) )
    {
      ArchiveEntry entry = null;
      while( (entry = i.getNextEntry()) != null )
      {
        if( !i.canReadEntryData( entry ) )
          throw new IllegalStateException();

        final Path targetFile = targetDir.resolve( entry.getName() );
        try( final OutputStream o = new BufferedOutputStream( Files.newOutputStream( targetFile ) ) )
        {
          IOUtils.copy( i, o );
        }
      }
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