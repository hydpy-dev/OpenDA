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

import java.util.concurrent.ThreadFactory;

/**
 * @author Gernot Belger
 */
final class HydPyThreadFactory implements ThreadFactory
{
  private final String m_name;

  public HydPyThreadFactory( final String name )
  {
    m_name = name;
  }

  @Override
  public Thread newThread( final Runnable r )
  {
    final ThreadGroup group = Thread.currentThread().getThreadGroup();
    final Thread thread = new Thread( group, r, m_name );
    thread.setDaemon( false );
    thread.setPriority( Thread.NORM_PRIORITY );
    return thread;
  }
}