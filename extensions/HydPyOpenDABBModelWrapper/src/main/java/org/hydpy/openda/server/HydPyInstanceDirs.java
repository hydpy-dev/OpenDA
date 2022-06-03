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
package org.hydpy.openda.server;

import java.io.File;

/**
 * @author Gernot Belger
 */
public final class HydPyInstanceDirs
{
  private final File m_inputConditionsDir;

  private final File m_outputConditionsDir;

  private final File m_seriesReaderDir;

  private final File m_seriesWriterDir;

  private final File m_outputControlDir;

  public HydPyInstanceDirs( final File inputConditionsDir, final File outputConditionsDir, final File seriesReaderDir, final File seriesWriterDir, final File outputControlDir )
  {
    m_inputConditionsDir = inputConditionsDir;
    m_outputConditionsDir = outputConditionsDir;
    m_seriesReaderDir = seriesReaderDir;
    m_seriesWriterDir = seriesWriterDir;
    m_outputControlDir = outputControlDir;
  }

  public File getInputConditionsDir( )
  {
    return m_inputConditionsDir;
  }

  public File getOutputConditionsDir( )
  {
    return m_outputConditionsDir;
  }

  public File getSeriesReaderDir( )
  {
    return m_seriesReaderDir;
  }

  public File getSeriesWriterDir( )
  {
    return m_seriesWriterDir;
  }

  public File getOutputControlDir( )
  {
    return m_outputControlDir;
  }
}