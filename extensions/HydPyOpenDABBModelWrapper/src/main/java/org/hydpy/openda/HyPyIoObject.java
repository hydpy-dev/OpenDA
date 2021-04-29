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
import java.util.List;

import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerException;
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.blackbox.interfaces.IoObjectInterface;
import org.openda.interfaces.IExchangeItem;

/**
 * @author Gernot Belger
 */
public final class HyPyIoObject implements IoObjectInterface
{
  private List<IExchangeItem> m_exchangeItems = null;

  private String m_instanceId = null;

  @Override
  public void initialize( final File workingDir, final String fileName, final String[] arguments )
  {
    try
    {
      if( arguments.length != 1 )
        throw new RuntimeException( "IO-Object expects exactly one argument, the instance-number" );

      m_instanceId = arguments[0];

      final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId );

      m_exchangeItems = server.getItemValues();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to initialize IO-Object", e );
    }
  }

  @Override
  public IExchangeItem[] getExchangeItems( )
  {
    return m_exchangeItems.toArray( new IExchangeItem[m_exchangeItems.size()] );
  }

  @Override
  public void finish( )
  {
    final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId );
    server.setItemValues( m_exchangeItems );
  }
}