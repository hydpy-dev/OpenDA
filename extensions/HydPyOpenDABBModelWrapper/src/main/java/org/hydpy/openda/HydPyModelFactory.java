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
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.hydpy.openda.server.HydPyModelInstance;
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
import org.openda.blackbox.config.DataObjectConfig;
import org.openda.blackbox.interfaces.IModelFactory;
import org.openda.blackbox.wrapper.BBModelFactory;
import org.openda.interfaces.IExchangeItem.Role;
import org.openda.interfaces.IModelInstance;
import org.openda.interfaces.IStochModelFactory.OutputLevel;
import org.openda.interfaces.ITime;

/**
 * A wrapper around the {@link BBModelFactory} that prepares a fixed configuration for use with HydPy.
 * Makes the stochModel.xml (blackBoxStochModel) and wrapper.xml (blackBoxWrapperConfig) obsolete, only the basic model.xml (blackBoxModelConfig) is used.
 * All information used in stochModel.xml and wrapper.xml is dynamically queried from the running HydPy instance (and configured within the HydPy model itself).
 *
 * @author Gernot Belger
 */
public class HydPyModelFactory implements IModelFactory
{
  private static final String TOKEN_INSTANCE_NUMBER = "%instanceNumber%";

  private static final String PROPERTY_CONFIG_FILE = "configFile"; //$NON-NLS-1$

  private static final String PROPERTY_TEMPLATE_DIR = "templateDir"; //$NON-NLS-1$

  private static final String PROPERTY_INSTANCE_DIR = "instanceDir"; //$NON-NLS-1$

  private static final String PROPERTY_INPUTCONDITIONSDIR = "inputConditionsDir"; //$NON-NLS-1$

  private static final String PROPERTY_OUTPUTCONDITIONSDIR = "outputConditionsDir"; //$NON-NLS-1$

  private static final String PROPERTY_SERIESWRITERDIR = "seriesWriterDir"; //$NON-NLS-1$

  private static final String PROPERTY_SERIESREADERDIR = "seriesReaderDir"; //$NON-NLS-1$

  private static final String IO_OBJECT_ID = "hydPyIo"; //$NON-NLS-1$

  private BBModelFactory m_bbFactory = null;

  private String m_templateDirPath;

  private String m_instanceDirPath;

  private File m_workingDir;

  @Override
  public void initialize( final File workingDir, final String[] arguments )
  {
    m_workingDir = workingDir;

    final Properties properties = parseArguments( arguments );
    final String filename = properties.getProperty( PROPERTY_CONFIG_FILE );

    // REMARK: the server may also have already been created via the observer, as they are initialized first.
    if( filename != null )
      HydPyServerManager.create( workingDir, filename );

    m_templateDirPath = properties.getProperty( PROPERTY_TEMPLATE_DIR );
    m_instanceDirPath = properties.getProperty( PROPERTY_INSTANCE_DIR );

    final String inputConditionsPath = properties.getProperty( PROPERTY_INPUTCONDITIONSDIR );
    final String outputConditionsPath = properties.getProperty( PROPERTY_OUTPUTCONDITIONSDIR );
    final String seriesReaderPath = properties.getProperty( PROPERTY_SERIESREADERDIR );
    final String seriesWriterPath = properties.getProperty( PROPERTY_SERIESWRITERDIR );
    final HydPyInstanceConfiguration instanceDirs = new HydPyInstanceConfiguration( workingDir, inputConditionsPath, outputConditionsPath, seriesReaderPath, seriesWriterPath );

    // REMARK: ugly post init, but hard to do otherwise.
    // The server might not have been created here, so we also cant use the 'create' method.
    HydPyServerManager.instance().init( instanceDirs );
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

  /**
   * Create factory on demand and not in {@link #initialize(File, String[])}, in order to avoid throwing exception in initialize.
   * This makes sure finish will more likely to be called in case of error, and so HydPy server instances will be shut down instead of beeing killed.
   */
  private synchronized BBModelFactory getFactory( )
  {
    if( m_bbFactory == null )
      m_bbFactory = createFactory();

    return m_bbFactory;
  }

  private BBModelFactory createFactory( )
  {
    final BBWrapperConfig wrapperConfig = initializeWrapperConfig( m_workingDir, m_templateDirPath, m_instanceDirPath );

    final HydPyModelInstance server = HydPyServerManager.instance().getOrCreateInstance( HydPyServerManager.ANY_INSTANCE, null );
    final Collection<IServerItem> items = server.getItems();
    final BBModelConfig bbModelConfig = initializeModelConfig( m_workingDir, wrapperConfig, items );

    return new BBModelFactory( bbModelConfig );
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
      final String message = String.format( "Either '%s' and '%s' must be both defined or none of them.", PROPERTY_TEMPLATE_DIR, PROPERTY_INSTANCE_DIR );
      throw new RuntimeException( message );
    }

    /* initialize-actions: do we need to clone a template directory or such? */
    final CloneType cloneType = hasDirs ? BBWrapperConfig.CloneType.Directory : BBWrapperConfig.CloneType.None;
    final String templateName = hasDirs ? templateDirPath : null;
    final String instanceName = hasDirs ? instanceDirPath : null;
    final Collection<BBAction> initializeActions = Collections.emptyList();

    final BBAction computeAction = initializeComputeAction( workingDir, aliasDefinitions );
    final Collection<BBAction> computeActions = Collections.singleton( computeAction );

    final Collection<BBAction> additionalComputeActions = Collections.emptyList();

    final Collection<BBAction> finalizeActions = Collections.emptyList();

    final HashMap<String, DataObjectConfig> ioObjects = initializeIoObjects( aliasDefinitions );

    return new BBWrapperConfig( aliasDefinitions, cloneType, templateName, instanceName, initializeActions, computeActions, additionalComputeActions, finalizeActions, ioObjects );
  }

  private BBAction initializeComputeAction( final File workingDir, final AliasDefinitions aliasDefinitions )
  {
    final File configDir = workingDir;
    final String exePath = null;
    final String className = HydPyComputeAction.class.getName();
    final String[] arguments = new String[] { TOKEN_INSTANCE_NUMBER };

    final String actualWorkingDirectory = null;

    final ArrayList<BBCheckOutput> checkOutputs = new ArrayList<>( /* Arrays.asList( checkOutput ) */ );
    final BBCheckReturnStatus checkReturnStatus = null; // new BBCheckReturnStatus( "0", aliasDefinitions );

    final boolean ignoreReturnStatus = false;

    return new BBAction( configDir, exePath, className, arguments, actualWorkingDirectory, checkOutputs, checkReturnStatus, ignoreReturnStatus, aliasDefinitions );
  }

  private HashMap<String, DataObjectConfig> initializeIoObjects( final AliasDefinitions aliasDefinitions )
  {
    final HashMap<String, DataObjectConfig> ioObjects = new HashMap<>();

    final String inputFileame = null;
    final String[] arguments = new String[] { TOKEN_INSTANCE_NUMBER };

    ioObjects.put( IO_OBJECT_ID, new DataObjectConfig( IO_OBJECT_ID, HydPyIoObject.class.getName(), inputFileame, aliasDefinitions, arguments ) );

    return ioObjects;
  }

  private BBModelConfig initializeModelConfig( final File workingDir, final BBWrapperConfig wrapperConfig, final Collection<IServerItem> items )
  {
    final File configRootDir = workingDir;
    // TODO: allow instancenumber format to be specified from outside?
    final String instanceNumberFormat = "0";

    final ITime startTime = null;
    final ITime endTime = null;
    final double timeStepMJD = Double.NaN;

    // REMARK: we always use these fixed exchange items to initially retrieve the simulation time span from the model.
    final String[] startTimeExchangeItemIds = new String[] { HydPyModelInstance.ITEM_ID_FIRST_DATE };
    final String[] endTimeExchangeItemIds = new String[] { HydPyModelInstance.ITEM_ID_LAST_DATE };
    final String[] timeStepExchangeItemIds = new String[] { HydPyModelInstance.ITEM_ID_STEP_SIZE };

    final Collection<BBModelVectorConfig> vectorConfigs = initializeVectorConfigs( wrapperConfig, items );

    final boolean skipModelActionsIfInstanceDirExists = false;
    final boolean doCleanUp = false;

    final String[] restartFileNames = new String[] {};
    // TODO: support saved state
    final String savedStatesDirPrefix = null;

    return new BBModelConfig( configRootDir, wrapperConfig, instanceNumberFormat, startTime, endTime, timeStepMJD, startTimeExchangeItemIds, endTimeExchangeItemIds, timeStepExchangeItemIds, vectorConfigs, skipModelActionsIfInstanceDirExists, doCleanUp, restartFileNames, savedStatesDirPrefix );
  }

  private Collection<BBModelVectorConfig> initializeVectorConfigs( final BBWrapperConfig wrapperConfig, final Collection<IServerItem> items )
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
    final BBModelFactory factory = getFactory();
    return factory.getInstance( arguments, outputLevel );
  }

  @Override
  public void finish( )
  {
    if( m_bbFactory != null )
      m_bbFactory.finish();

    // REMARK: this is never called (which it should be) from the BBStochModelFactory.
    // so we can't terminate HydPy here. for now, we do it in the HydPyStochModelFactory.
    // HydPyServerManager.instance().finish();
  }
}