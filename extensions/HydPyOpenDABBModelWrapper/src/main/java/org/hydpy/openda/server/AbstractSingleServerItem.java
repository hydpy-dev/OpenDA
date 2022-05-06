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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IExchangeItem.Role;

/**
 * Abstract implementation of server item that return exactly one exchange item.
 *
 * @author Gernot Belger
 */
abstract class AbstractSingleServerItem<TYPE> extends AbstractServerItem<TYPE>
{
  private final HydPyExchangeItemDescription m_description;

  public AbstractSingleServerItem( final String id, final Role role )
  {
    m_description = new HydPyExchangeItemDescription( id, role );
  }

  public final String getId( )
  {
    return m_description.getId();
  }

  @Override
  public final Collection<HydPyExchangeItemDescription> getExchangeItemDescriptions( )
  {
    return Collections.singleton( m_description );
  }

  @Override
  public final List<IExchangeItem> toExchangeItems( final TYPE value )
  {
    return Collections.singletonList( toExchangeItem( m_description.getId(), m_description.getRole(), value ) );
  }

  protected abstract IExchangeItem toExchangeItem( String id, Role role, final TYPE value );

  @Override
  public final TYPE toValue( final List<IExchangeItem> exItems )
  {
    return toValue( exItems.get( 0 ) );
  }

  protected abstract TYPE toValue( final IExchangeItem exItem );
}