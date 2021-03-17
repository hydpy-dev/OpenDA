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

import org.openda.interfaces.IArrayGeometryInfo;
import org.openda.interfaces.IStochVector;

/**
 * Abstraction for the spatial aspects of the maps noise. Hides the following aspects from the underlying algorithm:
 * - geometric dimensions (e.g. a 'grid' or maybe a irregular triangulation)
 * - the spatial correlation between elements (e.g. grid cells)
 *
 * @author Gernot Belger
 */
public interface ISpatialNoiseGeometry
{
  int[] getStateDimensions( );

  IArrayGeometryInfo getGeometryinfo( );

  IStochVector createSystemNoise( double standardWhiteNoise );
}