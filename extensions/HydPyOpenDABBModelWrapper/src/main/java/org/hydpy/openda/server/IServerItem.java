/**
 * Copyright (c) 2019 by
 * - Bundesanstalt f�r Gew�sserkunde
 * - Bj�rnsen Beratende Ingenieure GmbH
 * All rights reserved.
 *
 * This file is Free Software under the under the terms of the
 * GNU Lesser General Public License (LGPL >=v3)
 * and comes with ABSOLUTELY NO WARRANTY! Check out the
 * documentation coming with HydPy for details.
 */
package org.hydpy.openda.server;

import org.openda.interfaces.IPrevExchangeItem.Role;

/**
 * @author Gernot Belger
 */
public interface IServerItem
{
  String getId( );

  Role getRole( );
}