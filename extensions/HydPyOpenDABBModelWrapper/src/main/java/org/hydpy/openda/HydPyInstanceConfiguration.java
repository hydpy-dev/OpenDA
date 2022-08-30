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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

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
  private static final String PROPERTY_INPUTCONDITIONSDIR = "inputConditionsDir"; //$NON-NLS-1$

  private static final String PROPERTY_OUTPUTCONDITIONSDIR = "outputConditionsDir"; //$NON-NLS-1$

  private static final String PROPERTY_SERIESWRITERDIR = "seriesWriterDir"; //$NON-NLS-1$

  private static final String PROPERTY_SERIESREADERDIR = "seriesReaderDir"; //$NON-NLS-1$

  private static final String PROPERTY_OUTPUTCONTROLDIR = "outputControlDir"; //$NON-NLS-1$

  private static final String TOKEN_DEFAULT = "%"; //$NON-NLS-1$

  private static final String PROPERTY_TOKEN = "replacementToken"; //$NON-NLS-1$

  private final File m_workingDir;

  private final String m_inputConditionsPath;

  private final String m_outputConditionsPath;

  private final String m_seriesReaderPath;

  private final String m_seriesWriterPath;

  private final String m_outputControlPath;

  private final String m_replacementToken;

  public static HydPyInstanceConfiguration read( final File workingDir, final Properties properties )
  {
    final String inputConditionsPath = properties.getProperty( PROPERTY_INPUTCONDITIONSDIR );
    final String outputConditionsPath = properties.getProperty( PROPERTY_OUTPUTCONDITIONSDIR );
    final String seriesReaderPath = properties.getProperty( PROPERTY_SERIESREADERDIR );
    final String seriesWriterPath = properties.getProperty( PROPERTY_SERIESWRITERDIR );
    final String outputControlPath = properties.getProperty( PROPERTY_OUTPUTCONTROLDIR );

    final String replacementToken = properties.getProperty( PROPERTY_TOKEN, TOKEN_DEFAULT );

    return new HydPyInstanceConfiguration( workingDir, replacementToken, inputConditionsPath, outputConditionsPath, seriesReaderPath, seriesWriterPath, outputControlPath );
  }

  private HydPyInstanceConfiguration( final File workingDir, final String replacementToken, final String inputConditionsPath, final String outputConditionsPath, final String seriesReaderPath, final String seriesWriterPath, final String outputControlPath )
  {
    m_workingDir = workingDir;
    m_replacementToken = replacementToken;
    m_inputConditionsPath = inputConditionsPath;
    m_outputConditionsPath = outputConditionsPath;
    m_seriesReaderPath = seriesReaderPath;
    m_seriesWriterPath = seriesWriterPath;
    m_outputControlPath = outputControlPath;
  }

  public HydPyInstanceDirs resolve( final String instanceId, final File instanceDir, final File hydpyModelDir )
  {
    /* configure token replacements */
    final Map<String, String> replacements = new HashMap<>();
    replacements.put( "INSTANCEID", instanceId );
    if( instanceDir != null )
      replacements.put( "INSTANCEDIR", instanceDir.getAbsolutePath() );
    if( hydpyModelDir != null )
      replacements.put( "HYDPYMODELDIR", hydpyModelDir.getAbsolutePath() );
    if( m_workingDir != null )
      replacements.put( "WORKINGDIR", m_workingDir.getAbsolutePath() );

    final File inputConditionsDir = resolveDirectory( instanceId, replacements, m_inputConditionsPath, true );
    final File outputConditionsDir = resolveDirectory( instanceId, replacements, m_outputConditionsPath, false );
    final File seriesReaderDir = resolveDirectory( instanceId, replacements, m_seriesReaderPath, true );
    final File seriesWriterDir = resolveDirectory( instanceId, replacements, m_seriesWriterPath, false );
    final File outputControlDir = resolveDirectory( instanceId, replacements, m_outputControlPath, false );

    return new HydPyInstanceDirs( inputConditionsDir, outputConditionsDir, seriesReaderDir, seriesWriterDir, outputControlDir );
  }

  private File resolveDirectory( final String instanceId, final Map<String, String> replacements, final String path, final boolean checkExists )
  {
    if( HydPyServerManager.ANY_INSTANCE.equals( instanceId ) )
      return null;

    if( StringUtils.isBlank( path ) )
      return null;

    final String resolved = doTokenReplacement( path, replacements );

    final File resolvedDir = resolveAbsoluteOrRelativeDir( resolved );
    final File normalizedDir = resolvedDir.toPath().normalize().toFile();

    if( checkExists && !normalizedDir.exists() )
    {
      final String message = String.format( "Path '%s' resolved to '%s' but does not exist", path, normalizedDir );
      throw new RuntimeException( message );
    }

    return resolvedDir;
  }

  private String doTokenReplacement( final String toBeResolved, final Map<String, String> replacements )
  {
    String resolved = toBeResolved;

    for( final Entry<String, String> entry : replacements.entrySet() )
    {
      final String key = entry.getKey();
      final String value = entry.getValue();

      final String token = m_replacementToken + key + m_replacementToken;

      resolved = StringUtils.replace( resolved, token, value );
    }

    return resolved;
  }

  private File resolveAbsoluteOrRelativeDir( final String relativeOrAbsolutePath )
  {
    final File absolute = new File( relativeOrAbsolutePath );
    if( absolute.isAbsolute() )
      return absolute;

    return new File( m_workingDir, relativeOrAbsolutePath );
  }
}