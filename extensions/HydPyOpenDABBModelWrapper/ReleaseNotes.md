# Changes since v0.7.0

This is the first official release of the OpenDA HydPy wrapper after the intermediary release of 0.7.0.
since then, the supported OpenDA version switched to 3.0.0, and the supported HydPy version to 5.0.0.  

## New Features

* Added generalized spatial noise model implementation (independent of HydPy) that allows for more flexible handling of the spatial distribution than the original MapsNoiseModel of OpenDA.
* Support for HydPy's TimeSeries1D items (i.e. time series of several elements/nodes represented as a single exchange item).
  * Introduced the (provisional) possibility to split up Timeseries1D items (delivered by HydPY) into multiple exchange items (communicated to OpenDA). This happens for Timeseries1D items where the id contains '.split'. The item will be split up into exchange items with id '\<itemId\>.\<itemname\> where itemname is the name provided by HydPy via GET_itemnames.
* The configuration of the HydPy Server instances is now in a separate file in the java properties format. The file has to be referenced as the config file within the black box model configuration. E.g.: \<arg\>configFile:hydpy.properties\</arg\>.
* Added a bunch of new configuration parameters: 
  * Parameters that allow to set the paths to the python executable and the HydPy script.
  * Parameters that allow to start multiple HydPy server instances which are used for parallel execution of instance model runs.
  * 'logMode' parameter that allows more fine grained control over outputs of the wrapper.
  * Support for new HydPy features concerning 'inputconditions', 'outputconditions', 'serieswriterdir' and 'seriesreaderdir' via corresponding arguments. The arguments allow for instance based token replacements.
  * Parameter 'timeoutSeconds' that allows to specify the communication timeout between the wrapper and the HydPy servers.
  * Parameter 'outputControlDir'. If set, the wrapper will tell HydPy to write its current control parameters to this directory after each model instance run. The intentional use is to take over control parameters of calibration runs. 
* Introduced the argument 'instanceNumberFormat' in HydPyModelFactory that behaves as the same argument in BBModelFactory-xml. 
* The initial state (of all exchange item values) fetched from HydPy can now be shared between all ensemble instances, for items where it can safely be assumed that HydPy provides always the same values (e.g. observations). This will take place for exchange items that contain the text '.shared' within their id.
* Implemented support for restart files and saving/restoring the 'internal' state.
  * Added new option _suppressInternalStateSaving_ to configuration of _SpatialNoiseModelFactory_ which allows to suppress saving/restoring the internal state of the noise model. This will allow to use it in combination with the ParticleFilter. 

## Functionality Improvements

* Made process termination a bit more robust, especially in the case where server startup fails directly.
* Added client side communication (i.e. response content from the HydPyServer and content of POST calls) to the log.
* Introduced a version check against the currently used OpenDA and HydPy versions. Errors resp. warnings are produced if the versions do not match.
* Improved some error messages if available time range from HydPY does not match analysis times in OpenDA.

## Bug Fixes

* Explicitly declare time zone of firstdate/lastdate in hydpy.xml of examples, else they won't run on computers with a different system time.

## Backwards Compatibility

Backwards compatibility has been preserved as much as reasonably possible, however due to the extensive changes especially regarding Timeries1D items, could not always been kept.
Please refer to the current documentation on how to set up a wrapper project correctly. The most notable imcompatibilities are:

* org.hydpy.openda.HydPyStochModelFactory has been removed. Any use (typically in main.oda) must be replaced with org.openda.blackbox.wrapper.BBStochModelFactory.
* The configuration of the HydPy Server has been moved to a separate file in java properties format. Properties previously defined within the \<arg\> tags of model.xml files must be declared within this separated file.  
* Similar, the configuration for the instance specific series and condition directories, prior in model.xml, had to be moved to the hydpy.properties configuration..
* Changed the behavior of the 'instanceDir' argument in wrapped blackBoxStochModel configuration file (i.e. model.xml). InstanceNumber must now be specified. See documentation for new syntax.
