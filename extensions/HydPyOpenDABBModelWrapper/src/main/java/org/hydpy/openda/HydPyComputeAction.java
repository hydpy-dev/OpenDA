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

import java.io.File;

import org.hydpy.openda.server.HydPyServer;
import org.hydpy.openda.server.HydPyServerException;
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.interfaces.IConfigurable;

/**
 * @author Gernot Belger
 */
public class HydPyComputeAction implements IConfigurable
{
  /**
   * @param arguments
   *          Expects the instance-id as single argument.
   */
  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    final String instanceId = arguments[0];

    try
    {
      final HydPyServer server = HydPyServerManager.instance().getOrCreateServer();
      server.simulate( instanceId );
    }
    catch( final HydPyServerException e )
    {
      throw new RuntimeException( e );
    }
  }
}