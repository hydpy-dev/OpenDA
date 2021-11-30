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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerException;
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.interfaces.IDataObject;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
public final class HyPyIoObject implements IDataObject
{
  private final Map<String, IExchangeItem> m_exchangeItems = new HashMap<>();

  private String m_instanceId = null;

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    try
    {
      final String filename = arguments[0];
      if( !StringUtils.isBlank( filename ) )
      {
        /* this case should happen, if used in combination with org.openda.observers.IoObjectStochObserver */
        HydPyServerManager.create( workingDir, filename );
        m_instanceId = HydPyServerManager.ANY_INSTANCE;
      }
      else
      {
        /* this case usually happens if called from the BlackBoxModel wrapper */
        if( arguments.length < 2 )
          throw new RuntimeException( "IO-Object expects at least two arguments, the second one is the instance-number" );

        m_instanceId = arguments[1];
      }

      final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId );

      final List<IExchangeItem> exchangeItems = server.getItemValues();
      for( final IExchangeItem item : exchangeItems )
        m_exchangeItems.put( item.getId(), item );
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to initialize IO-Object", e );
    }
  }

  @Override
  public String[] getExchangeItemIDs( )
  {
    return m_exchangeItems.keySet().toArray( new String[m_exchangeItems.size()] );
  }

  @Override
  public String[] getExchangeItemIDs( final Role role )
  {
    return m_exchangeItems.values()//
        .stream()//
        .filter( item -> item.getRole() == role )//
        .map( item -> item.getId() )//
        .toArray( String[]::new );
  }

  @Override
  public IExchangeItem getDataObjectExchangeItem( final String exchangeItemID )
  {
    return m_exchangeItems.get( exchangeItemID );
  }

  @Override
  public void finish( )
  {
    final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId );
    server.setItemValues( m_exchangeItems.values() );
  }
}