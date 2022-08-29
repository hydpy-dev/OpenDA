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
package org.hydpy.openda.server;

/**
 * @author Gernot Belger
 */
public final class HydPyServerException extends RuntimeException
{
  public HydPyServerException( final String message )
  {
    super( message );
  }

  public HydPyServerException( final Throwable cause )
  {
    super( cause );
  }

  public HydPyServerException( final String message, final Throwable cause )
  {
    super( message, cause );
  }
}