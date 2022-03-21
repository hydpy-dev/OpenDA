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
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.interfaces.IDataObject;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * @author Gernot Belger
 */
public final class HydPyIoObject implements IDataObject
{
  private Map<String, IExchangeItem> m_exchangeItems = null;

  private String m_instanceId = null;

  private File m_workingDir = null;

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    m_workingDir = workingDir;

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
  }

  private synchronized Map<String, IExchangeItem> getExchangeItemsInternal( )
  {
    if( m_exchangeItems == null )
      m_exchangeItems = fetchExchangeItems();

    return m_exchangeItems;
  }

  private Map<String, IExchangeItem> fetchExchangeItems( )
  {
    try
    {
      final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId, m_workingDir );

      final List<IExchangeItem> exchangeItems = server.getItemValues();

      final Map<String, IExchangeItem> id2Items = new HashMap<>();
      for( final IExchangeItem item : exchangeItems )
        id2Items.put( item.getId(), item );

      return id2Items;
    }
    catch( final Exception e )
    {
      e.printStackTrace();

      throw new RuntimeException( "Failed to initialize IO-Object", e );
    }
  }

  @Override
  public String[] getExchangeItemIDs( )
  {
    final Map<String, IExchangeItem> exchangeItems = getExchangeItemsInternal();

    return exchangeItems.keySet().toArray( new String[exchangeItems.size()] );
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
    final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( m_instanceId, null );
    server.setItemValues( m_exchangeItems.values() );
  }
}