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
package org.hydpy.openda.noise;

import org.openda.exchange.ArrayExchangeItem;

/**
 * @author Gernot Belger
 */
final class MapsNoiseModelItem
{
  private final String m_id;

  private final ArrayExchangeItem m_outputSeries;

  private final MapsNoiseModelConfigurationItem m_configItem;

  public MapsNoiseModelItem( final String id, final MapsNoiseModelConfigurationItem configItem, final ArrayExchangeItem outputSeries )
  {
    m_id = id;
    m_configItem = configItem;
    m_outputSeries = outputSeries;
  }

  public String getId( )
  {
    return m_id;
  }

  public ArrayExchangeItem getOutputSeries( )
  {
    return m_outputSeries;
  }

  public int getSizeX( )
  {
    return m_configItem.getSizeX();
  }

  public int getSizeY( )
  {
    return m_configItem.getSizeY();
  }
}