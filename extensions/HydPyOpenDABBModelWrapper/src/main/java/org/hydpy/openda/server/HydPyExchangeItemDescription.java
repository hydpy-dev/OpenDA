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
package org.hydpy.openda.server;

import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
public final class HydPyExchangeItemDescription
{
  private final String m_id;

  private final Role m_role;

  public HydPyExchangeItemDescription( final String id, final Role role )
  {
    m_id = id;
    m_role = role;
  }

  public String getId( )
  {
    return m_id;
  }

  public Role getRole( )
  {
    return m_role;
  }
}