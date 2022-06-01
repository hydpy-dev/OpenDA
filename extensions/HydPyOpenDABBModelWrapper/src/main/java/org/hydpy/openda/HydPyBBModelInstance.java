/**
 * Copyright (c) 2022 by
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

import java.lang.reflect.Field;

import org.hydpy.openda.server.HydPyModelInstance;
import org.hydpy.openda.server.HydPyServerException;
import org.hydpy.openda.server.HydPyServerManager;
import org.openda.blackbox.config.BBModelConfig;
import org.openda.blackbox.wrapper.BBModelInstance;
import org.openda.interfaces.IModelState;
import org.openda.interfaces.ITime;

/**
 * @author Gernot Belger
 */
final class HydPyBBModelInstance extends BBModelInstance
{
  public HydPyBBModelInstance( final BBModelConfig modelConfig, final int instanceNumber, final ITime timeHorizon )
  {
    super( modelConfig, instanceNumber, timeHorizon );
  }

  @Override
  public IModelState saveInternalState( )
  {
    // REMARK: we need to access any exchange item at this point in order to force
    // waiting for any simulation of this instance to be finished.
    // Accessing an exchange item will indirectly call getItemValue on the hydpy instance
    // which will block until the simulation has finished and returned its current item values.

    // This is necessary in the case, where we write 'state conditions' for every simulation run.
    // Which in turn is necessary for some algorithms like the ParticleFilter.

    // This can happen, if the algorithm saves the internal state (as files via BBModel stuff) before
    // accessing the item values.

    // TODO: maybe we should really tell hyd py now to save its conditions file, instead of
    // writing if after each simulation automatically

    final String instanceId = getInstanceId();
    try
    {
      final HydPyModelInstance instance = HydPyServerManager.instance().getOrCreateInstance( instanceId, getModelRunDir() );
      instance.getItemValues();
    }
    catch( final HydPyServerException e )
    {
      e.printStackTrace();
      throw new RuntimeException( e );
    }

//    final String[] exchangeItemIDs = getExchangeItemIDs();
//    /* final IExchangeItem exchangeItem = */getExchangeItem( exchangeItemIDs[0] );

    return super.saveInternalState();
  }

  @Override
  public void restoreInternalState( final IModelState savedInternalState )
  {
    super.restoreInternalState( savedInternalState );

    final String instanceId = getInstanceId();

    // REMARK: we now tell HydPy to load the previously saved conditions file and register it for
    // the given instance
    final HydPyModelInstance instance = HydPyServerManager.instance().getOrCreateInstance( instanceId, getModelRunDir() );
    instance.restoreInternalState();
  }

  private String getInstanceId( )
  {
    try
    {
      final Field declaredField = BBModelInstance.class.getDeclaredField( "instanceNumberString" );
      declaredField.setAccessible( true );
      return (String)declaredField.get( this );
    }
    catch( final Exception e )
    {
      /* OpenDA style error handling */
      throw new RuntimeException( e );
    }
  }
}