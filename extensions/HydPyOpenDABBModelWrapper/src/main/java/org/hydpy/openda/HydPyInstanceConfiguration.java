/**
 * Copyright (c) 2021 by
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

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.hydpy.openda.server.HydPyInstanceDirs;
import org.hydpy.openda.server.HydPyServerManager;

/**
 * Holds all information about the input/output directories for a model instance which may vary on the instance.
 *
 * @author Gernot Belger
 */
public final class HydPyInstanceConfiguration
{
  private final File m_workingDir;

  private final String m_inputConditionsPath;

  private final String m_outputConditionsPath;

  private final String m_seriesReaderPath;

  private final String m_seriesWriterPath;

  public HydPyInstanceConfiguration( final File workingDir, final String inputConditionsPath, final String outputConditionsPath, final String seriesReaderPath, final String seriesWriterPath )
  {
    m_workingDir = workingDir;
    m_inputConditionsPath = inputConditionsPath;
    m_outputConditionsPath = outputConditionsPath;
    m_seriesReaderPath = seriesReaderPath;
    m_seriesWriterPath = seriesWriterPath;
  }

  public HydPyInstanceDirs resolve( final String instanceId, final File instanceDir, final File hydpyModelDir )
  {
    final File inputConditionsDir = resolveDirectory( instanceId, instanceDir, m_inputConditionsPath, hydpyModelDir, true );
    final File outputConditionsDir = resolveDirectory( instanceId, instanceDir, m_outputConditionsPath, hydpyModelDir, false );
    final File seriesReaderDir = resolveDirectory( instanceId, instanceDir, m_seriesReaderPath, hydpyModelDir, true );
    final File seriesWriterDir = resolveDirectory( instanceId, instanceDir, m_seriesWriterPath, hydpyModelDir, false );

    return new HydPyInstanceDirs( inputConditionsDir, outputConditionsDir, seriesReaderDir, seriesWriterDir );
  }

  private File resolveDirectory( final String instanceId, final File instanceDir, final String path, final File hydpyModelDir, final boolean checkExists )
  {
    if( HydPyServerManager.ANY_INSTANCE.equals( instanceId ) )
      return null;

    if( StringUtils.isBlank( path ) )
      return null;

    String resolved = path;

    resolved = StringUtils.replace( resolved, "%INSTANCEID%", instanceId );
    resolved = StringUtils.replace( resolved, "%INSTANCEDIR%", instanceDir.getAbsolutePath() );
    resolved = StringUtils.replace( resolved, "%HYDPYMODELDIR%", hydpyModelDir.getAbsolutePath() );
    resolved = StringUtils.replace( resolved, "%WORKINGDIR%", m_workingDir.getAbsolutePath() );

    final File resolvedDir = resolveAbsoluteOrRelativeDir( resolved );
    final File normalizedDir = resolvedDir.toPath().normalize().toFile();

    if( checkExists && !normalizedDir.isDirectory() )
    {
      final String message = String.format( "Path '%s' resolved to '%s' but does not exist", path, normalizedDir );
      throw new RuntimeException( message );
    }

    return resolvedDir;
  }

  private File resolveAbsoluteOrRelativeDir( final String relativeOrAbsolutePath )
  {
    final File absolute = new File( relativeOrAbsolutePath );
    if( absolute.isAbsolute() )
      return absolute;

    return new File( m_workingDir, relativeOrAbsolutePath );
  }
}