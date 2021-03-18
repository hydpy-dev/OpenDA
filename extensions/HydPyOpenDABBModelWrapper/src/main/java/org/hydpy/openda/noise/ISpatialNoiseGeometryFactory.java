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
package org.hydpy.openda.noise;

import org.openda.utils.ConfigTree;

/**
 * Factory for creating {@link ISpatialNoiseGeometry}. Implementors of this factory can be used in the 'factory' attribute of the geometry tag of a noise configuration xml file.
 *
 * @author Gernot Belger
 */
public interface ISpatialNoiseGeometryFactory
{
  /**
   * Creates a new {@link ISpatialNoiseGeometry} from the xml-snippet of its definition in the corresponding xml file.
   */
  ISpatialNoiseGeometry create( ConfigTree correlationConfig );
}