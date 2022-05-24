# HydPyOpenDABBModelWrapper

Allows running a [HydPy](https://github.com/hydpy-dev/hydpy) model as a 
BlackBoxModel in [OpenDA](http://openda.org/).

This version of _HydPyOpenDABBModelWrapper_ is currently compatible with OpenDA Version 3.0.x. 

You can download the pre-compiled version of the wrapper [here](https://github.com/hydpy-dev/OpenDA/releases) at GitHub and directly skip to [installation](#installation). 

## Installation

To install the _HydPyOpenDABBModelWrapper_ into [OpenDA](http://openda.org/), 
unzip the contents of _HydPyOpenDABBModelWrapper.zip_ into the _bin_ 
directory of your OpenDA installation.

If you update to another wrapper version, make sure to delete all files from the previous installation.

## Usage

Please see to the [HydPy-OpenDA example projects](../../demos).

### Prerequisites

* A HydPy installation, see the [HydPy documentation](https://hydpy-dev.github.io/hydpy/master).
* An OpenDA installation, see the [OpenDA documentation](http://openda.org/index.php/documentation).
* A running HydPy project, see the [LahnH example project](https://hydpy-dev.github.io/hydpy/master/examples.html#hydpy.examples.prepare_full_example_1)

### OpenDA configuration 

OpenDA must be configured and run as usual via its main _.oda_ file.

HydPy is integrated as an OpenDA "BlackBoxModel", so the _stochModelFactory_ entry of the _.oda_ file needs to select the usual `org.openda.blackbox.wrapper.BBStochModelFactory` class.

For example:
```xml
	<stochModelFactory className="org.openda.blackbox.wrapper.BBStochModelFactory">
		<workingDirectory></workingDirectory>
		<configFile>model.xml</configFile>
	</stochModelFactory> 
```

The `org.openda.blackbox.wrapper.BBStochModelFactory` uses the _model.xml_ file as its configuration. 
However, we need to use the HydPy specific `org.hydpy.openda.HydPyModelFactory` instead of the usual 
`org.openda.blackbox.wrapper.BBModelFactory`.

Usually, the _model.xml_ file refers to two additional configuration files, typically called 
_stochModel.xml_ and _wrapper.xml_. However, the HydPy wrapper generates 
both files internally, as it retrieves all information from the HydPy 
project configuration (see below). 

The rest of the _model.xml_ file (e.g. the `vectorSpecification`) is configured in the usual way. 
Use the id's from the _hydpy.xml_ configuration file (see below) as parameters (exchange item) id's here.

Example:
```xml
<blackBoxStochModel xmlns="http://www.openda.org" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openda.org http://schemas.openda.org/blackBoxStochModelConfig.xsd">
  <modelFactory className="org.hydpy.openda.HydPyModelFactory" workingDirectory=".">
    <arg>configFile:hydpy.properties</arg>
  </modelFactory>
  ...
</blackBoxStochModel>
```

The model factory supports the following arguments:

* configFile (string, partly required): The configuration file for the HydPy Server instances. This option is not required if also a '`HyPyIoObject`' is used within the stoch observer (see below).
* templateDir (string, optional): The template directory path for model instances.  
* instanceDir (string, optional): The instance directory path for model instances. The place holder %incanceNumber% within the path can (and should) be used for algorithms that start multiple model instances.
* instanceNumberFormat (fixed values, optional): How instance number gets formatted into the instance directories (original option from BBStochModel). Allowed values are '0', '00', '000' and '0000', determining on how many digits the instance number will be left-padded with '0's. E.g. for '000', instance 1 will be printed as '001', instance 17 as '017' and instance 999 as '999'.    

If any of these paths is relative, it gets resolved against the working directory. 

The HyPy wrapper does not use the template and instance directories, but 
some algorithms write (debug) output to the instances directories if 
existing. If this output is of interest, both directory arguments must
be specified.

#### HydPy Configuration File

The required configuration file for the HydPy server instances contains all information required to start HydPy and is in the [Java Properties file format](https://en.wikipedia.org/wiki/.properties).
It also allows to configure model-instance specific input and output directories. 
The following properties are supported: 

* serverPort (integer): The web port on which to start the HydPy server. Use any free port on your machine. 
* serverInstances (integer, optional): The number of HydPy server processes that will be started (maximal). Defaults to 1. If greater 1 and the chosen algorithm allows multiple instances, _HydPyOpenDABBModelWrapper_ will automatically start several server instances and run simulations in parallel. Server instances will run on web ports starting with _serverPort_ up-to _serverPort+serverInstances-1_.
* serverParallelStartup (boolean, optional): If server instances should be started in parallel at the beginning of the simulation. Defaults to 'false'. If 'true', ALL instances (regardless of how many are really used) will be started directly and parallel to each other on startup which can be significantly faster for many instances.
* serverPreStarted (boolean, optional): If set to 'true' the wrapper assumes HydPy to already have been started on the right port(s) and does not try to start (or stop) the process by itself. Defaults to 'false'. This flag is mainly meant for debug purposes.       
* initializeWaitSeconds (integer): The maximum time in seconds the wrapper implementation should wait for the HydPy server to start up. This time may depend on the actual HydPy project. Increase this if 'serverParallelStartup' is set to 'true', as starting several python processes at once will slow down the start-up time of each process.
* timeoutSeconds (integer, optional): The maximum time the wrapper waits for the HydPy server to respond. Defaults to 60 seconds. This needs to be increased for long simulation periods and/or large models. Set to 0 to deactivate, which might lead to a blocked process.  
* projectPath (string): The path to the HydPy project directory.
* projectName (string): The name of the HydPy project within the project directory.
* configFile (string): The name of the [HydPy servertools](https://hydpy-dev.github.io/hydpy/master/servertools.html) configuration file.
* logMode (string, optional): Options for enabling additional logging. Possible values are 'off' (default if not set), 'console' and 'file'. If activated (i.e. not 'off') the client calls to the HydPy server instances are logged, as well as the process outputs of the HydPy server instances. If set to 'file', the argument 'logDirectory' must also be set. If set to 'console' all these output will be written to the console output stream of the OpenDa process.
* logDirectory (string, optional): The path to the directory where the additional logging output be written. 'logMode' must be set to 'file' to activate this. If specified, for each server instance, three log files will be written:
  * HydPy\_Client\_\<instanceId\>.log: the calls from the wrapper to the HydPy server instances.
  * HydPy\_Server\_\<instanceId\>.log: the process output stream of the HydPy server instance
  * HydPy\_Server\_\<instanceId\>.err: the process error stream of the HydPy server instance
* inputConditionsDir (string, optional): The directory path from where initial conditions will be read (per model instance). Supports additional placeholder tokens, see below. 
* outputConditionsDir (string, optional): The directory path where the conditions at the end of the simulation will be written (per model instance). Supports additional placeholder tokens, see below.
* seriesReaderDir (string, optional): The directory path from where time series will be read (per model instance) by HydPy. Supports additional placeholder tokens, see below.
* seriesWriterDir (string, optional): The directory path where time series will be written to (per model instance). Supports additional placeholder tokens, see below.
* outputControlDir (string, optional): The directory path where the control parameters will be written for each model run.
* stateConditionsFile (string, optional): The relative file path within an instanceDir (as defined in the xml configuration of the black box stoch model). If defined, the wrapper will tell HydPy to write its current conditions to this file on every model simulation. See below for an in depth explanation. Must end with '.zip'.

The wrapper resolves all arguments denoting files or directories relative to the working directory of the factory.

The arguments _inputConditionsDir_, _outputConditionsDir_, _seriesReaderDir_, _seriesWriterDir_ and _outputControlDir_ can contain the following placeholder tokens:
 * INSTANCEID: the instance number, formatted as used in _instanceDir_ 
 * INSTANCEDIR: the path to the _instanceDir_
 * HYDPYMODELDIR: the HydPy project directory as specified in the _configFile_
 * WORKINGDIR : the working directory (not required as pathes are resolved against the working dir, but can be used to make this more explicit)

#### Note on observations
To access observed values, the usual mechanisms of OpenDA apply, and any existing _stochObserver_ can be used in the usual way.
However, it is possible (and convenient) to access the observed values directly from the HydPy model, as these are often already contained within the model. 
This especially avoids the necessity to convert the observed values in any format known by the OpenDA implementation.

In order to achieve this, the wrapper also supplies a `org.openda.interfaces.IDataObject` implementation (namely `org.hydpy.openda.HyPyIoObject`), 
that can be used in combination with the `org.openda.observers.IoObjectStochObserver`.  

The `HyPyIoObject` gets as argument the filename to the same type of configuration file as would get the `HydPyModelFactory`.
The configuration file contains the same possible options as listed above.

Example of a stoch observer configuration using `HyPyIoObject`

In the .oda file:
```xml
  <stochObserver className="org.openda.observers.IoObjectStochObserver">
    <workingDirectory></workingDirectory>
    <configFile>observations.xml</configFile>
  </stochObserver>
```

In the `observations.xml` file. 
```xml
<dataObjectStochObserver xmlns="http://www.openda.org">
  <!--- usual OpenDA configuration of the uncertainty engine omitted -->

  <dataObject workingDirectory="." className="org.hydpy.openda.HyPyIoObject">
    <fileName>hydpy.properties</fileName>
  </dataObject>

</dataObjectStochObserver>
```

Note: If you use both the `HyPyIoObject` and the `HydPyModelFactory`, only the `HyPyIoObject` needs the `hydpy.properties` file as it is initialized first by OpenDA. 

#### Note on parallelisation
The HydPyOpenDABBModelWrapper may parallelize and hence run faster by distributing simulation runs onto multiple running HydPy Servers (see option 'serverInstances'). 
This however depends strongly on the chosen algorithm, which in turn determines the call order to the underlying model. 
Especially for ensemble based algorithms (e.g. Ensemble Kalman Filter) this might result in a tremendous speed up. Advised number of server instances is the number of
simulated ensemble members (note that e.g. in case of ENKF this is the configured number plus one extra 'mean' member), which is equal to the number of 
model-instances created by OpenDA, or whole divisors of that number.
Apart from the chosen algorithm, keep in mind that also the number of physical available processors and the execution time of one single model simulation do influence the optimal number
of server instances.

#### Not on stateConditionsFile
As stated if set, the wrapper tells HydPy to write conditions on every model run. HydPy must be configured to write conditions as zip archives, as OpenDA can only handle individual files.
The wrapper will automatically add this file to the internal state of each model instance.

This is necessary, if algorithms are used, that use the internal model state, e.g. the ParticleFilter.
If the algorithm is based on ensemble instances, it is also necessary to define the 'dirPrefix' within the 'restartInfo' tag of the stoch model xml configuration to use instance based instances.
This is achieved by prefixing the 'dirPrefix' with 'INSTANCE_DIR/', for example
```xml
	<restartInfo dirPrefix="INSTANCE_DIR/savedStochModelState_"/>
```

### HydPy Project Configuration 

The HydPy OpenDA wrapper is based on the [HydPy servertools](https://hydpy-dev.github.io/hydpy/master/servertools.html), 
which starts HydPy in a REST-full server mode, which in turn is controlled by the OpenDA wrapper. 
This requires the HydPy project to be set-up in the servertools specific way.

In particular, part of the usual OpenDa BlackBoxModel configuration (i.e. _stochModel.xml_ and _wrapper.xml_) is situated in the 
HydPy project itself (in form of the _hydpy.xml_ file), as the HydPy server must know which model parameters,
states, and time series have to be manipulated by OpenDA, and how.

The HydPy server exhibits the configured HydPy exchange items, 
which in turn are configured as OpenDA exchange items by the wrapper 
implementation. 

The main starting point is the _hydpy.xml_ configuration file. See for 
example the one of the [calibration example](../../demos/openda_projects/DUD).

### Running OpenDA

With proper configuration, start the main OpenDa batch file, usually via 
`oda_run_batch.bat myOdaFile.oda`.

In turn, the HyPy wrapper starts the 
[HydPy server](https://hydpy-dev.github.io/hydpy/master/servertools.html), which is 
optimized for fast communication with OpenDA.

Depending on your HyPy and [Python](https://www.python.org/) installation two additional system environment variables can be set:
* HYD_PY_PYTHON_EXE: Path to the python.exe (defaults to 'python.exe') 
* HYD_PY_SCRIPT_PATH: Path to the hyd.py script. (defaults to 'hyd.py')

With [Python](https://www.python.org/) already on your system path, you may not need to configure these system variables.

## Compilation

Building the sources is done via [Gradle](https://gradle.org/). Usually,`gradle build` should be enough to create all artifacts.

The release will be distributed under 
_HydPyOpenDABBModelWrapper\build\distributions\HydPyOpenDABBModelWrapper.zip_

[Eclipse](https://www.eclipse.org/) is used as IDE for development, and all 
required Eclipse artifacts are part of the sources. 
You need the Eclipse Gradle integration, 
[Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship), 
as well.

## Debugging

Debugging OpenDA and the wrapper implementation from Eclipse works well, 
just create a _Java Application_ configuration with main class 
`org.openda.application.OpenDaApplication`.

Then add the following to the class path of the launch configuration:
 * all jars (or all least the openda_core.jar) from an existing OpenDA installation _bin_ directory
 * the _HydPyOpenDABBModelWrapper_
