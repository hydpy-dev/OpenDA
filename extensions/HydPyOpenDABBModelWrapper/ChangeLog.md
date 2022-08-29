# Changes since v0.7.0

Switched to version 3.0.0 of OpenDA.

Support for HydPy's TimeSeries1D items (i.e. time series of several elements/nodes represented as a single exchange item).

Introduced 'logMode' parameter that allows more fine grained control over outputs of the wrapper.

Added client side communication (i.e. response content from the HydPyServer and content of POST calls) to the log.

org.hydpy.openda.HydPyStochModelFactory is now obsolete. Any use should (typically in main.oda) be replaced with org.openda.blackbox.wrapper.BBStochModelFactory.

The configuration of the HydPy Server instances has been now (in a non backwards compatible way) moved to a separate file in java properties format.
Within the <modelFactory> element of the <blackBoxStochModel> configuration file, the new argument 'configFile' is used to reference the HydPy-Server properties file relative to the working directory.  
E.g.:
    <arg>configFile:hydpy.properties</arg>

Changed the behavior of the 'instanceDir' argument in wrapped blackBoxStochModel configuration file (i.e. model.xml). InstanceNumber must now be specified. See documentation for new syntax.

Support for new HydPy features concerning 'inputconditions', 'outputconditions', 'serieswriterdir' and 'seriesreaderdir' via corresponding arguments. The arguments allow for instance based token replacements.
This allows for a model instance based reading and writing of:
* start conditions
* end conditions
* input timeseries
* output timeseries 

Introduced version check against the currently used OpenDA version.

Introduced the property 'instanceNumberFormat' argument in HydPyModelFactory that behaves as the same argument in BBModelFactory-xml. 
