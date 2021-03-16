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

import org.hydpy.openda.noise.SpatialCorrelationStochVector.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.exchange.QuantityInfo;
import org.openda.utils.Matrix;

final class MapsNoiseModelConfigurationItem
{
  private static final double degreesToRadians = Math.PI / 180.0;

  private static final double radiusEarth = 6372800.0; // average radius in leastsquares sense in meters

  private final String m_id;

  private final QuantityInfo m_quantityInfo;

  private final CoordinatesType m_coordsType;

  private final boolean m_separable;

  private final double m_initialValue;

  private final double m_stdDeviation;

  private final double m_timeCorrelationScale;

  private final double m_horizontalCorrelationScale;

  private final double[] m_x;

  private final double[] m_y;

  private SpatialCorrelationCovariance m_sharedCorrelationCovariance = null;

  private SpatialCorrelationCovariance m_sharedCorrelationCovarianceX = null;

  private SpatialCorrelationCovariance m_sharedCorrelationCovarianceY = null;

  private final double m_incrementTime;

  private final ArrayGeometryInfo m_geometryInfo;

  public MapsNoiseModelConfigurationItem( final String id, final double incrementTime, final QuantityInfo quantityInfo, final CoordinatesType coordsType, final ArrayGeometryInfo geometryInfo, final boolean separable, final double initialValue, final double stdDeviation, final double timeCorrelationScale, final double horizontalCorrelationScale, final double[] x, final double[] y )
  {
    m_id = id;
    m_incrementTime = incrementTime;
    m_quantityInfo = quantityInfo;
    m_coordsType = coordsType;
    m_geometryInfo = geometryInfo;
    m_separable = separable;
    m_initialValue = initialValue;
    m_stdDeviation = stdDeviation;
    m_timeCorrelationScale = timeCorrelationScale;
    m_horizontalCorrelationScale = horizontalCorrelationScale;
    m_x = x;
    m_y = y;
  }

  public String getId( )
  {
    return m_id;
  }

  public QuantityInfo getQuantityInfo( )
  {
    return m_quantityInfo;
  }

  public ArrayGeometryInfo getGeometryInfo( )
  {
    return m_geometryInfo;
  }

  public boolean isSeparable( )
  {
    return m_separable;
  }

  public double getInitialValue( )
  {
    return m_initialValue;
  }

  public int getSizeX( )
  {
    return m_x.length;
  }

  public int getSizeY( )
  {
    return m_y.length;
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

  // REMARK: we share the SpatialCorrelationCovariance between instances (per item-id).
  public synchronized SpatialCorrelationCovariance getSpatialCorrelationCovariance( )
  {
    if( m_separable )
      throw new IllegalStateException( "Should not access shared covariante if separable" );

    if( m_sharedCorrelationCovariance == null )
    {
      final double x2[] = new double[m_x.length * m_y.length];
      final double y2[] = new double[m_x.length * m_y.length];
      expandGrid( m_x, m_y, x2, y2 );

      m_sharedCorrelationCovariance = createSpatialCorrelationCovariance( x2, y2 );
    }

    return m_sharedCorrelationCovariance;
  }

  // REMARK: we share the SpatialCorrelationCovariance between instances (per item-id).
  public synchronized SpatialCorrelationCovariance getSpatialCorrelationCovarianceX( )
  {
    if( !m_separable )
      throw new IllegalStateException( "Should not access shared x-covariante if not separable" );

    if( m_sharedCorrelationCovarianceX == null )
    {
      final double midY = 0.5 * (m_y[0] + m_y[m_y.length - 1]);
      final double[] midYCopies = new double[m_x.length];
      for( int ix = 0; ix < m_x.length; ix++ )
        midYCopies[ix] = midY;

      m_sharedCorrelationCovarianceX = createSpatialCorrelationCovariance( m_x, midYCopies );
    }

    return m_sharedCorrelationCovarianceX;
  }

  public SpatialCorrelationCovariance getSpatialCorrelationCovarianceY( )
  {
    if( !m_separable )
      throw new IllegalStateException( "Should not access shared y-covariante if not separable" );

    if( m_sharedCorrelationCovarianceY == null )
    {
      final double midX = 0.5 * (m_x[0] + m_x[m_x.length - 1]); // assume regular grid
      final double[] midXCopies = new double[m_y.length];
      for( int iy = 0; iy < m_y.length; iy++ )
        midXCopies[iy] = midX;

      m_sharedCorrelationCovarianceY = createSpatialCorrelationCovariance( midXCopies, m_y );
    }

    return m_sharedCorrelationCovarianceY;
  }

  private SpatialCorrelationCovariance createSpatialCorrelationCovariance( final double[] x, final double[] y )
  {
    final int n = x.length;
    final Matrix covariance = new Matrix( n, n );

    final double standardDeviation = getStandardWhiteNoise();

    final double scale = standardDeviation * standardDeviation;

    final double lengthScaleSquare = m_horizontalCorrelationScale * m_horizontalCorrelationScale;

    for( int i = 0; i < n; i++ )
    {
      for( int j = 0; j < n; j++ )
      {
        final double dist = distance( x[i], y[i], x[j], y[j] );
        final double covij = scale * Math.exp( -0.5 * dist * dist / lengthScaleSquare );
        covariance.setValue( i, j, covij );
      }
    }

    final double determinantCov = covariance.determinant();
    final Matrix sqrtCovariance = covariance.sqrt();

    return new SpatialCorrelationCovariance( n, sqrtCovariance, determinantCov );
  }

  private double distance( final double x1, final double y1, final double x2, final double y2 )
  {
    if( m_coordsType == CoordinatesType.WGS84 )
    {
      // simplified computation with sphere and chord length
      // we can consider both cheaper and more accurate alternatives
      final double lon1 = x1 * degreesToRadians;
      final double lat1 = y1 * degreesToRadians;
      final double lon2 = x2 * degreesToRadians;
      final double lat2 = y2 * degreesToRadians;
      final double dX = Math.cos( lat2 ) * Math.cos( lon2 ) - Math.cos( lat1 ) * Math.cos( lon1 ); // 3d on sphere with unit length
      final double dY = Math.cos( lat2 ) * Math.sin( lon2 ) - Math.cos( lat1 ) * Math.sin( lon1 );
      final double dZ = Math.sin( lat2 ) - Math.sin( lat1 );
      return radiusEarth * 2.0 * Math.asin( 0.5 * Math.sqrt( dX * dX + dY * dY + dZ * dZ ) );
    }
    else
      return Math.sqrt( (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) );
  }

  private static void expandGrid( final double x[], final double y[], final double x2[], final double y2[] )
  {
    int index = 0;
    for( final double element : x )
    {
      for( final double element2 : y )
      {
        x2[index] = element;
        y2[index] = element2;
        index++;
      }
    }
  }
}