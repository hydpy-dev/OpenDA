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
package org.hydpy.openda.noise;

import org.openda.interfaces.IArrayGeometryInfo;
import org.openda.interfaces.IStochVector;

/**
 * Abstraction for the spatial aspects of the maps noise. Hides the following aspects from the underlying algorithm:
 * - geometric dimensions (e.g. a 'grid' or maybe a irregular mesh)
 * - the spatial correlation between elements (e.g. grid cells)
 *
 * @author Gernot Belger
 */
public interface ISpatialNoiseGeometry
{
  /**
   * Get the dimensions of the state vector of the associated (noise-) exchange item. E.g for a grid of n x-coordinates and m y-coordinates, we get [n,m].
   */
  int[] getStateDimensions( );

  /**
   * Returns the geometry info to be used in the noise exchange item, which is eventually be used within the OpenDA framework to transform between model- and noise- exchange items.
   */
  IArrayGeometryInfo getGeometryinfo( );

  /**
   * Create a new stochastic vector that realizes the noise on the exchange item.
   */
  IStochVector createSystemNoise( double standardWhiteNoise );
}