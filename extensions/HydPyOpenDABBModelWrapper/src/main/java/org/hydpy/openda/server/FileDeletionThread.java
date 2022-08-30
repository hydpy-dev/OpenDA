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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

/**
 * @author Gernot Belger
 */
public final class FileDeletionThread extends Thread
{
  private final BlockingQueue<File> m_queue = new LinkedBlockingQueue<>( 1000 );

  private final static FileDeletionThread INSTANCE = new FileDeletionThread();

  static
  {
    INSTANCE.start();
  }

  public static FileDeletionThread instance( )
  {
    return INSTANCE;
  }

  public FileDeletionThread( )
  {
    setDaemon( true );
  }

  public void addFilesForDeletion( final List<File> filesToDelete )
  {
    for( final File file : filesToDelete )
    {
      try
      {
        m_queue.offer( file, 1, TimeUnit.MINUTES );
      }
      catch( final InterruptedException e )
      {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void run( )
  {
    while( true )
    {
      try
      {
        final File file = m_queue.take();

        try
        {
          FileUtils.forceDelete( file );
        }
        catch( final IOException e )
        {
          e.printStackTrace();
          m_queue.offer( file, 5, TimeUnit.SECONDS );
        }
      }
      catch( final InterruptedException e )
      {
        e.printStackTrace();
      }
    }
  }
}