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
package org.hydpy.openda.noise.points;

import org.hydpy.openda.noise.ISpatialNoiseGeometry;
import org.hydpy.openda.noise.ISpatialNoiseGeometryFactory;
import org.hydpy.openda.noise.SpatialNoiseUtils;
import org.hydpy.openda.noise.SpatialNoiseUtils.CoordinatesType;
import org.openda.utils.ConfigTree;

/**
 * @author Gernot Belger
 */
public final class SpatialNoisePointsGeometryFactory implements ISpatialNoiseGeometryFactory
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
    final CoordinatesType coordinatesType = SpatialNoiseUtils.parseCoordinatesType( config );

    // spatial correlation
    final double horizontalCorrelationScale = SpatialNoiseUtils.parseHorizontalCorrelationScale( config );

    final double[] x = SpatialNoiseUtils.parseCoordinates( config, "x" );
    final double[] y = SpatialNoiseUtils.parseCoordinates( config, "y" );

    if( x.length != y.length )
      throw new RuntimeException( "points-geometry must have same number of x and y coordinates" );

    return new SpatialNoisePointsGeometry( coordinatesType, horizontalCorrelationScale, x, y );
  }
}