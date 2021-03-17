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
import org.openda.exchange.QuantityInfo;
import org.openda.interfaces.IPrevExchangeItem.Role;
import org.openda.interfaces.IStochVector;

final class SpatialNoiseModelConfigurationItem
{
  private final String m_id;

  private final QuantityInfo m_quantityInfo;

  private final double m_initialValue;

  private final double m_stdDeviation;

  private final double m_timeCorrelationScale;

  private final double m_incrementTime;

  private final ISpatialNoiseGeometry m_correlation;

  public SpatialNoiseModelConfigurationItem( final String id, final double incrementTime, final QuantityInfo quantityInfo, final double initialValue, final double stdDeviation, final double timeCorrelationScale, final ISpatialNoiseGeometry correlation )
  {
    m_id = id;
    m_incrementTime = incrementTime;
    m_quantityInfo = quantityInfo;
    m_correlation = correlation;
    m_initialValue = initialValue;
    m_stdDeviation = stdDeviation;
    m_timeCorrelationScale = timeCorrelationScale;
  }

  public String getId( )
  {
    return m_id;
  }

  public QuantityInfo getQuantityInfo( )
  {
    return m_quantityInfo;
  }

  public double getInitialValue( )
  {
    return m_initialValue;
  }

  public double getAlpha( )
  {
    return Math.exp( -m_incrementTime / m_timeCorrelationScale );
  }

  public double getStandardWhiteNoise( )
  {
    final double alpha = getAlpha();

    return m_stdDeviation * Math.sqrt( 1 - alpha * alpha );
  }

  public ArrayExchangeItem createStateExchangeItem( )
  {
    final ArrayExchangeItem outputItem = new ArrayExchangeItem( m_id, Role.Output );
    outputItem.setQuantityInfo( m_quantityInfo );
    outputItem.setGeometryInfo( m_correlation.getGeometryinfo() );
    return outputItem;
  }

  public IStochVector createSystemNoise( )
  {
    final double standardWhiteNoise = getStandardWhiteNoise();

    return m_correlation.createSystemNoise( standardWhiteNoise );
  }

  public int[] getStateDimensions( )
  {
    return m_correlation.getStateDimensions();
  }
}