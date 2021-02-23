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
import java.util.List;

import org.openda.interfaces.IPrevExchangeItem;

/**
 * @author Gernot Belger
 */
public interface IHydPyServer
{
  String ITEM_ID_FIRST_DATE = "firstdate_sim"; //$NON-NLS-1$

  String ITEM_ID_LAST_DATE = "lastdate_sim"; //$NON-NLS-1$

  String ITEM_ID_STEP_SIZE = "stepsize"; //$NON-NLS-1$

  List<IServerItem> getItems( );

  List<IPrevExchangeItem> getItemValues( String instanceId ) throws HydPyServerException;

  void setItemValues( String instanceId, Collection<IPrevExchangeItem> values ) throws HydPyServerException;

  void simulate( String instanceId ) throws HydPyServerException;
}