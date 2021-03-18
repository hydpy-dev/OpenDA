/**
 * Copyright (c) 2019 by
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
package org.hydpy.openda.noise.grid;

import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.ISpatialNoiseGeometryFactory;
import org.hydpy.openda.noise.SpatialNoiseUtils;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.exchange.ArrayGeometryInfo;
import org.openda.exchange.QuantityInfo;
import org.openda.utils.Array;
import org.openda.utils.ConfigTree;

/**
 * Implementation of {@link ISpatialNoiseGeometryFactory} for grid-like geometries assuming a Gaussian distribution and a distance based correlation.
 * <br/>
 * To speed up calculations the coordinates can be treated as separable. This means that correlations
 * are computed for both spatial directions separately. This is much faster, but is only approximate for
 * spherical coordinates, especially near the poles.
 *
 * @author verlaanm
 * @author Gernot Belger
 */
public final class SpatialNoiseGridGeometryFactory implements ISpatialNoiseGeometryFactory
{
  /*
   * spatial grid
   * <grid coordinates="wgs84" separable="true">
   * <x>-5,0,...,5</x>
   * <y>50,55,...,60</y>
   * </grid>
   */
  @Override
  public ISpatialNoiseGeometry create( final ConfigTree config )
  {
    final CoordinatesType coordsType = SpatialNoiseUtils.parseCoordinatesType( config );

    final boolean separable = config.getAsBoolean( "@separable", true );

    // spatial correlation
    final double horizontalCorrelationScale = SpatialNoiseUtils.parseHorizontalCorrelationScale( config );

    final double[] x = SpatialNoiseUtils.parseCoordinates( config, "x" );
    final double[] y = SpatialNoiseUtils.parseCoordinates( config, "y" );

    final Array latitudeArray = new Array( y, new int[] { y.length }, true );
    final Array longitudeArray = new Array( x, new int[] { x.length }, true );
    final int latitudeValueIndices[] = new int[] { 1 }; // we use the order time, lat, lon according to CF
    final int longitudeValueIndices[] = new int[] { 2 };
    final QuantityInfo latitudeQuantityInfo = null;
    final QuantityInfo longitudeQuantityInfo = null;
    final ArrayGeometryInfo geometryInfo = new ArrayGeometryInfo( latitudeArray, latitudeValueIndices, latitudeQuantityInfo, longitudeArray, longitudeValueIndices, longitudeQuantityInfo, null, null, null );

    if( separable )
      return new SpatialNoiseSeparableGridGeometry( horizontalCorrelationScale, coordsType, geometryInfo, x, y );
    else
      return new SpatialNoiseGridGeometry( horizontalCorrelationScale, coordsType, geometryInfo, x, y );
  }
}