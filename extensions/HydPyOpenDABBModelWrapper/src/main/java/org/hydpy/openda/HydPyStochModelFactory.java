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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hydpy.openda.server.HydPyServerManager;
import org.openda.blackbox.config.BBCartesianToPolarConfig;
import org.openda.blackbox.config.BBNoiseModelConfig;
import org.openda.blackbox.config.BBRegularisationConstantConfig;
import org.openda.blackbox.config.BBStochModelConfig;
import org.openda.blackbox.config.BBStochModelConfigReader;
import org.openda.blackbox.config.BBStochModelVectorConfig;
import org.openda.blackbox.config.BBStochModelVectorsConfig;
import org.openda.blackbox.config.NoiseModelExchangeItemConfig;
import org.openda.blackbox.wrapper.BBStochModelFactory;

/**
 * @author Gernot Belger
 */
public class HydPyStochModelFactory extends BBStochModelFactory
{
  @Override
  public void initialize( final File configRootDir, final String[] arguments )
  {
    /* pre-read model.xml as we have no legal access to it */
    final String configFileName = arguments[0];
    final BBStochModelConfigReader stochModelConfigReader = new BBStochModelConfigReader( new File( configRootDir, configFileName ) );
    final BBStochModelConfig stochModelConfig = stochModelConfigReader.getBBStochModelConfig();

    /* find out which exchange-items should be 'fixed' and which not */
    final Collection<String> fixedItems = extractFixedParameters( stochModelConfig );
    HydPyServerManager.initFixedItems( fixedItems );

    /* proceed with init */
    super.initialize( configRootDir, arguments );
  }

  // FIXME: comment, why do we need this?
  private Collection<String> extractFixedParameters( final BBStochModelConfig stochModelConfig )
  {
    final Set<String> fixedParameters = new HashSet<>();

    final BBStochModelVectorsConfig stochModelVectorsConfig = stochModelConfig.getBbStochModelVectorsConfig();

    /* uncertainty models */
    final List<BBNoiseModelConfig> paramsUncertaintyModelConfigs = stochModelVectorsConfig.getParamsUncertaintyModelConfigs();
    for( final BBNoiseModelConfig noiseModelConfig : paramsUncertaintyModelConfigs )
    {
      final List<NoiseModelExchangeItemConfig> exchangeItemConfigs = noiseModelConfig.getExchangeItemConfigs();
      for( final NoiseModelExchangeItemConfig exchangeItemConfig : exchangeItemConfigs )
      {
        fixedParameters.add( exchangeItemConfig.getId() );
        fixedParameters.addAll( exchangeItemConfig.getModelExchangeItemIds() );
      }
    }

    /* regularisation constants */
    final Collection<BBRegularisationConstantConfig> regularisationConstantCollection = stochModelVectorsConfig.getRegularisationConstantCollection();
    for( final BBRegularisationConstantConfig regularisationConstantConfig : regularisationConstantCollection )
    {
      final List<BBStochModelVectorConfig> vectorConfigs = regularisationConstantConfig.getVectorConfigs();
      for( final BBStochModelVectorConfig vectorConfig : vectorConfigs )
      {
        fixedParameters.add( vectorConfig.getId() );
        // TODO: also sourceId? or which one?
      }
    }

    /* cartesian to polar */
    final Collection<BBCartesianToPolarConfig> cartesianToPolarCollection = stochModelVectorsConfig.getCartesianToPolarCollection();
    for( final BBCartesianToPolarConfig cartesianToPolarConfig : cartesianToPolarCollection )
    {
      final List<BBStochModelVectorConfig> vectorConfigs = cartesianToPolarConfig.getVectorConfigs();
      for( final BBStochModelVectorConfig vectorConfig : vectorConfigs )
      {
        fixedParameters.add( vectorConfig.getId() );
        // TODO: also sourceId? or which one?
      }
    }

    return fixedParameters;
  }

  @Override
  public void finish( )
  {
    HydPyServerManager.instance().finish();

    super.finish();
  }
}