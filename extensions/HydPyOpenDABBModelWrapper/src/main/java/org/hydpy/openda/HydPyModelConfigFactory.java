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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.hydpy.openda.server.HydPyServer;
import org.hydpy.openda.server.HydPyServerException;
import org.hydpy.openda.server.HydPyServerManager;
import org.hydpy.openda.server.IServerItem;
import org.openda.blackbox.config.AliasDefinitions;
import org.openda.blackbox.config.BBAction;
import org.openda.blackbox.config.BBCheckOutput;
import org.openda.blackbox.config.BBCheckReturnStatus;
import org.openda.blackbox.config.BBModelConfig;
import org.openda.blackbox.config.BBModelVectorConfig;
import org.openda.blackbox.config.BBWrapperConfig;
import org.openda.blackbox.config.BBWrapperConfig.CloneType;
import org.openda.blackbox.config.IoObjectConfig;
import org.openda.blackbox.interfaces.IModelFactory;
import org.openda.blackbox.wrapper.BBModelFactory;
import org.openda.interfaces.IModelInstance;
import org.openda.interfaces.IPrevExchangeItem.Role;
import org.openda.interfaces.IStochModelFactory.OutputLevel;
import org.openda.interfaces.ITime;

/**
 * A wrapper around the {@link BBModelFactory} that prepares a fixed configuration for use with HydPy.
 * Makes the stochModel.xml (blackBoxStochModel) and wrapper.xml (blackBoxWrapperConfig) obsolete, only the basic model.xml (blackBoxModelConfig) is used.
 * All information used in stochModel.xml and wrapper.xml is dynamically queried from the running HydPy instance (and configured within the HydPy model itself).
 *
 * @author Gernot Belger
 */
public class HydPyModelConfigFactory implements IModelFactory
{
  private static final String PROPERTY_TEMPLATE_DIR_PATH = "templateDir"; //$NON-NLS-1$

  private static final String PROPERTY_INSTANCE_DIR_PATH = "instanceDir"; //$NON-NLS-1$

  private static final String IO_OBJECT_ID = "hydPyIo"; //$NON-NLS-1$

  private BBModelFactory m_bbFactory;

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    try
    {
      final Properties args = parseArguments( arguments );

      final String templateDirPath = args.getProperty( PROPERTY_TEMPLATE_DIR_PATH );
      final String instanceDirPath = args.getProperty( PROPERTY_INSTANCE_DIR_PATH );

      HydPyServerManager.create( workingDir.toPath(), args );

      final BBWrapperConfig wrapperConfig = initializeWrapperConfig( workingDir, templateDirPath, instanceDirPath );

      final HydPyServer server = HydPyServerManager.instance().getOrCreateServer();
      final List<IServerItem> items = server.getItems();
      final BBModelConfig bbModelConfig = initializeModelConfig( workingDir, wrapperConfig, items );

      m_bbFactory = new BBModelFactory( bbModelConfig );
    }
    catch( final HydPyServerException e )
    {
      throw new RuntimeException( e );
    }
  }

  private Properties parseArguments( final String[] arguments )
  {
    final Properties properties = new Properties();

    for( final String argument : arguments )
    {
      final String[] split = StringUtils.split( argument, ":", 2 );
      if( split.length == 2 )
        properties.setProperty( split[0], split[1] );
    }

    return properties;
  }

  private BBWrapperConfig initializeWrapperConfig( final File workingDir, final String templateDirPath, final String instanceDirPath )
  {
    // REMARK: the alias definition comes normally from the wrapper-config-xml, but this does not exist
    // using our dedicated model factory.

    final AliasDefinitions aliasDefinitions = new AliasDefinitions();

    final String keyPrefix = "%";
    final String keySuffix = "%";

    // REMARK: these are necessary because they are used within the BB implementation
    aliasDefinitions.add( "instanceNumber", keyPrefix, keySuffix, "-1", null );
    aliasDefinitions.add( "currentTime", keyPrefix, keySuffix, "0.0", null );
    aliasDefinitions.add( "targetTime", keyPrefix, keySuffix, "0.0", null );

    // REMARK: some algorithms (e.g. Sequential) need instance dirs in order to write some intermediate output
    // like the armaNoiseModel-log.txt. This only works if we actually define the dirs.
    if( templateDirPath != null )
      aliasDefinitions.add( "templateDir", keyPrefix, keySuffix, templateDirPath, null );
    if( instanceDirPath != null )
      aliasDefinitions.add( "instanceDir", keyPrefix, keySuffix, instanceDirPath, null );
    // aliasDefinitions.add( "inputFile", keyPrefix, keySuffix, "reactive_pollution_model.input", null );
    // aliasDefinitions.add( "outputFile", keyPrefix, keySuffix, "reactive_pollution_model.output", null );

    final boolean hasDirs = templateDirPath != null && instanceDirPath != null;
    if( hasDirs )
    {
      // Actually create the empty template dir, else we get problems later
      final File templateDir = new File( workingDir, templateDirPath );
      templateDir.mkdirs();
    }
    else if( templateDirPath == null && instanceDirPath == null )
    {
      /* good case */
    }
    else
    {
      final String message = String.format( "Either '%s' and '%s' must be both defined or none of them.", PROPERTY_TEMPLATE_DIR_PATH, PROPERTY_INSTANCE_DIR_PATH );
      throw new RuntimeException( message );
    }

    /* initialize-actions: do we need to clone a template directory or such? */
    final CloneType cloneType = hasDirs ? BBWrapperConfig.CloneType.Directory : BBWrapperConfig.CloneType.None;
    final String templateName = hasDirs ? "%templateDir%" : null;
    final String instanceName = hasDirs ? "%instanceDir%%instanceNumber%" : null;
    final Collection<BBAction> initializeActions = Collections.emptyList();

    final BBAction computeAction = initializeComputeAction( workingDir, aliasDefinitions );
    final Collection<BBAction> computeActions = Collections.singleton( computeAction );

    final Collection<BBAction> additionalComputeActions = Collections.emptyList();

    final Collection<BBAction> finalizeActions = Collections.emptyList();

    final HashMap<String, IoObjectConfig> ioObjects = initializeIoObjects( aliasDefinitions );

    return new BBWrapperConfig( aliasDefinitions, cloneType, templateName, instanceName, initializeActions, computeActions, additionalComputeActions, finalizeActions, ioObjects );
  }

  private BBAction initializeComputeAction( final File workingDir, final AliasDefinitions aliasDefinitions )
  {
    final File configDir = workingDir;
    final String exePath = null;
    final String className = HydPyComputeAction.class.getName();
    final String[] arguments = new String[] { "%instanceNumber%" };

    final String actualWorkingDirectory = null;

    final ArrayList<BBCheckOutput> checkOutputs = new ArrayList<>( /* Arrays.asList( checkOutput ) */ );
    final BBCheckReturnStatus checkReturnStatus = null; // new BBCheckReturnStatus( "0", aliasDefinitions );

    final boolean ignoreReturnStatus = false;

    return new BBAction( configDir, exePath, className, arguments, actualWorkingDirectory, checkOutputs, checkReturnStatus, ignoreReturnStatus, aliasDefinitions );
  }

  private HashMap<String, IoObjectConfig> initializeIoObjects( final AliasDefinitions aliasDefinitions )
  {
    final HashMap<String, IoObjectConfig> ioObjects = new HashMap<>();

    final String inputFileame = null;
    final String[] arguments = new String[] { "%instanceNumber%" };
    ioObjects.put( IO_OBJECT_ID, new IoObjectConfig( IO_OBJECT_ID, HyPyIoObject.class.getName(), inputFileame, aliasDefinitions, arguments ) );

    return ioObjects;
  }

  private BBModelConfig initializeModelConfig( final File workingDir, final BBWrapperConfig wrapperConfig, final List<IServerItem> items )
  {
    final File configRootDir = workingDir;
    final String instanceNumberFormat = "0";

    final ITime startTime = null;
    final ITime endTime = null;
    final double timeStepMJD = Double.NaN;

    // REMARK: we always use these fixed exchange items to initially retrieve the simulation time span from the model.
    final String[] startTimeExchangeItemIds = new String[] { HydPyServer.ITEM_ID_FIRST_DATE };
    final String[] endTimeExchangeItemIds = new String[] { HydPyServer.ITEM_ID_LAST_DATE };
    final String[] timeStepExchangeItemIds = new String[] { HydPyServer.ITEM_ID_STEP_SIZE };

    final Collection<BBModelVectorConfig> vectorConfigs = initializeVectorConfigs( wrapperConfig, items );

    final boolean skipModelActionsIfInstanceDirExists = false;
    final boolean doCleanUp = false;

    final String[] restartFileNames = new String[] {};
    final String savedStatesDirPrefix = null;

    return new BBModelConfig( configRootDir, wrapperConfig, instanceNumberFormat, startTime, endTime, timeStepMJD, startTimeExchangeItemIds, endTimeExchangeItemIds, timeStepExchangeItemIds, vectorConfigs, skipModelActionsIfInstanceDirExists, doCleanUp, restartFileNames, savedStatesDirPrefix );
  }

  private Collection<BBModelVectorConfig> initializeVectorConfigs( final BBWrapperConfig wrapperConfig, final List<IServerItem> items )
  {
    final Collection<BBModelVectorConfig> vectorConfigs = new ArrayList<>();

    for( final IServerItem item : items )
    {
      final String itemId = item.getId();
      final Role role = item.getRole();
      vectorConfigs.add( new BBModelVectorConfig( itemId, wrapperConfig.getIoObject( IO_OBJECT_ID ), itemId, null, null, role, null ) );
    }

    return vectorConfigs;
  }

  @Override
  public IModelInstance getInstance( final String[] arguments, final OutputLevel outputLevel )
  {
    return m_bbFactory.getInstance( arguments, outputLevel );
  }

  @Override
  public void finish( )
  {
    m_bbFactory.finish();
  }

  public static void main( final String[] args )
  {
    throw new UnsupportedOperationException();
  }
}