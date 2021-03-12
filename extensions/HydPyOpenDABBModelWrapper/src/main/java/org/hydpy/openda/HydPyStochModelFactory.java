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
package org.hydpy.openda;

import org.hydpy.openda.server.HydPyServerManager;
import org.openda.blackbox.wrapper.BBStochModelFactory;

/**
 * @author Gernot Belger
 */
public class HydPyStochModelFactory extends BBStochModelFactory
{
  @Override
  public void finish( )
  {
    HydPyServerManager.instance().finish();

    super.finish();
  }
}