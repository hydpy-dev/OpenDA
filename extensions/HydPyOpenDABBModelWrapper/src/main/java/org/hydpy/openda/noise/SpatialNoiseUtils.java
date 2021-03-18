/**
 * Copyright (c) 2021 by
 * - OpenDA Association
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

import org.apache.commons.lang3.StringUtils;
import org.openda.utils.ConfigTree;

/**
 * @author verlaanm
 * @author Gernot Belger
 */
public final class SpatialNoiseUtils
{
  private static final double radiusEarth = 6372800.0; // average radius in least squares sense in meters

  // distances are different on a globe
  public enum CoordinatesType
  {
    WGS84,
    XY
  }

  private SpatialNoiseUtils( )
  {
    throw new UnsupportedOperationException();
  }

  public static CoordinatesType parseCoordinatesType( final ConfigTree config )
  {
    final String coordsTypeValue = config.getAsString( "@coordinates", CoordinatesType.WGS84.name() ).toUpperCase();
    return CoordinatesType.valueOf( coordsTypeValue );
  }

  public static double parseHorizontalCorrelationScale( final ConfigTree config )
  {
    final double horizontalCorrelationScaleValue = config.getAsDouble( "@horizontalCorrelationScale", 0.0 );
    final String horizontalCorrelationScaleUnit = config.getAsString( "@horizontalCorrelationScaleUnit", "m" ).trim().toLowerCase();
    return adjustHorizontalCorrelationScale( horizontalCorrelationScaleValue, horizontalCorrelationScaleUnit );
  }

  private static double adjustHorizontalCorrelationScale( final double horizontalCorrelationScale, final String horizontalCorrelationScaleUnit )
  {
    switch( horizontalCorrelationScaleUnit )
    {
      case "m":
        return horizontalCorrelationScale * 1.0;

      case "km":
        return horizontalCorrelationScale * 1000.;

      case "cm":
        return horizontalCorrelationScale * 0.01;

      default:
        throw new RuntimeException( "unknown horizontalCorrelationScaleUnit." + " Expected (m,km,cm),but found " + horizontalCorrelationScaleUnit );
    }
  }

  /**
   * Parse a string with a list of numbers eg 1.0,2.0,3.0
   * or 1.0,2.0,...,10.0 for sequence with fixed steps
   * into a double[] array
   *
   * @param valuesString
   *          String containing the double values
   * @return values as doubles
   */
  public static double[] parseCoordinates( final ConfigTree config, final String attribute )
  {
    final String valuesString = config.getContentString( attribute );
    if( StringUtils.isBlank( valuesString ) )
      throw new RuntimeException( String.format( "MapsNoisModelInstance: missing attribute '%s'", attribute ) );

    try
    {
      final String parts[] = valuesString.trim().split( "," );
      if( !valuesString.contains( "..." ) )
      {
        // eg 1.0,2.0,3.0
        final int n = parts.length;

        final double[] result = new double[n];
        for( int j = 0; j < n; j++ )
          result[j] = Double.parseDouble( parts[j] );

        return result;
      }
      else
      {
        final double start = Double.parseDouble( parts[0] );
        final double step = Double.parseDouble( parts[1] ) - start;
        final double stop = Double.parseDouble( parts[3] );
        final int n = (int)Math.round( (stop - start) / step ) + 1;

        final double[] result = new double[n];
        for( int i = 0; i < n; i++ )
          result[i] = start + i * step;

        return result;
      }
    }
    catch( final NumberFormatException e )
    {
      throw new RuntimeException( String.format( "Problems parsing '%s'", attribute ), e );
    }
  }

  public static double distance( final CoordinatesType coordsType, final double x1, final double y1, final double x2, final double y2 )
  {
    if( coordsType == CoordinatesType.WGS84 )
    {
      // simplified computation with sphere and chord length
      // we can consider both cheaper and more accurate alternatives
      final double lon1 = Math.toRadians( x1 );
      final double lat1 = Math.toRadians( y1 );
      final double lon2 = Math.toRadians( x2 );
      final double lat2 = Math.toRadians( y2 );
      final double dX = Math.cos( lat2 ) * Math.cos( lon2 ) - Math.cos( lat1 ) * Math.cos( lon1 ); // 3d on sphere with unit length
      final double dY = Math.cos( lat2 ) * Math.sin( lon2 ) - Math.cos( lat1 ) * Math.sin( lon1 );
      final double dZ = Math.sin( lat2 ) - Math.sin( lat1 );
      return radiusEarth * 2.0 * Math.asin( 0.5 * Math.sqrt( dX * dX + dY * dY + dZ * dZ ) );
    }
    else
      return Math.sqrt( (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) );
  }
}