# HydPyOpenDABBModelWrapper

Allows running a [HydPy](https://github.com/hydpy-dev/hydpy) model as a 
BlackBoxModel in [OpenDA](http://openda.org/).

This version of HydPyOpenDABBModelWrapper is currently compatible with OpenDA Version 2.4.5. 

## Compilation

Building the sources is done via [Gradle](https://gradle.org/). Usually,
`gradle build` should be enough to create all artefacts.

The release will be distributed under 
_HydPyOpenDABBModelWrapper\build\distributions\HydPyOpenDABBModelWrapper.zip_

[Eclipse](https://www.eclipse.org/) is used as IDE for development, and all 
required Eclipse artefacts are part of the sources. 
You need the Eclipse Gradle integration, 
[Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship), 
as well.

## Debugging

Debugging OpenDA and the wrapper implementation from Eclipse works well, 
just create a _Java Application_ configuration with main class 
`org.openda.application.OpenDaApplication`.

Include all jars from an existing OpenDA installation _bin_ directory 
into the launch configuration.

## Installation

To install the _HydPyOpenDABBModelWrapper_ into [OpenDA](http://openda.org/), 
unzip the contents of _HydPyOpenDABBModelWrapper.zip_ into the _bin_ 
directory of your OpenDA installation.

## Usage

Please see to the [HydPy-OpenDA example projects](../../demos).

### Prerequisites

* A HydPy installation, see the [HydPy documentation](https://hydpy-dev.github.io/hydpy/master).
* An OpenDA installation, see the [OpenDA documentation](http://openda.org/index.php/documentation).
* A running HydPy project, see the [LahnH example project](https://hydpy-dev.github.io/hydpy/master/examples.html#hydpy.examples.prepare_full_example_1)

### OpenDA configuration 

OpenDA must be configured and run as usual via its main _.oda_ file.

HydPy is integrated as an OpenDA "BlackBoxModel" but changes it in some subtle
ways.

So the _stochModelFactory_ entry of the _.oda_ file needs to select the HydPy 
specific `org.hydpy.openda.HydPyStochModelFactory` class (instead of the usual 
`org.openda.blackbox.wrapper.BBStochModelFactory`).

For example:
```xml
	<stochModelFactory className="org.openda.blackbox.wrapper.BBStochModelFactory">
		<workingDirectory></workingDirectory>
		<configFile>model.xml</configFile>
	</stochModelFactory> 
```

The `org.hydpy.openda.HydPyStochModelFactory` uses the same XML file format as 
`org.openda.blackbox.wrapper.BBStochModelFactory`, as the model configuration 
(here `main.xml`). However, we need to use the HydPy specific 
`org.hydpy.openda.HydPyModelFactory`  instead of the usual 
`org.openda.blackbox.wrapper.BBModelFactory`.

Usually, the _model.xml_ file refers to two additional configuration files, the
_stochModel.xml_ and _wrapper.xml_. However, the HydPy wrapper generates 
both files internally, as it retrieves all information from the HydPy 
project configuration (see below). 

The rest of the _model.xml_ file (e.g. the `vectorSpecification`) is configured in the usual way. 
Use the id's from the _hydpy.xml_ configuration file (see below) as parameters id's here.

Example:
```xml
	<modelFactory className="org.hydpy.openda.HydPyModelFactory" workingDirectory=".">
		<arg>serverPort:8080</arg>
		<arg>serverInstances:60</arg>
		<arg>serverParallelStartup:true</arg>
		<arg>initializeWaitSeconds:5</arg>
		<arg>projectPath:../../hydpy_projects</arg>
		<arg>projectName:LahnH</arg>
		<arg>configFile:hydpy.xml</arg>
		<arg>logDirectory:results/logs</arg>
	</modelFactory>
```

The model factory requires the following arguments

* serverPort (integer): The web port on which to start the HydPy server. Use any free port on your machine. 
* serverInstances (integer, optional): The number of HydPy server processes that will be started (maximal). Defaults to 1. If greater 1 and the chosen algorithm allows multiple instances, _HydPyOpenDABBModelWrapper_ will automatically start several server instances and run simulations in parallel. Server instances will run on web ports starting with _serverPort_ up-to _serverPort+serverInstances-1_.
* serverParallelStartup (boolean, optional): If server instances should be started in parallel at the beginning of the simulation. Defaults to 'false'. If 'true', ALL instances (regardless of how many are really used) will be started directly and parallel to each other on startup which can be significantly faster for many instances.
* serverPreStarted (boolean, optional): If set to 'true' the wrapper assumes HydPy to already have been started on the right port(s) and does not try to start (or stop) the process by itself. Defaults to 'false'. This flag is mainly meant for debug purposes.       
* initializeWaitSeconds (integer): The maximum time in seconds the wrapper implementation should wait for the HydPy server to start up. This time may depend on the actual HydPy project. Increase this if 'serverParallelStartup' is set to 'true', as starting several python processes at once will slow down the start-up time of each process. 
* projectPath (string): The path to the HydPy project directory.
* projectName (string): The name of the HydPy project within the project directory.
* configFile (string): The name of the [HydPy servertools](https://hydpy-dev.github.io/hydpy/master/servertools.html) configuration file.
* logDirectory (string, optional): The path to the directory where the output of the HydPy server processes will be written. If specified, for each server instance, two log files (one for the standard output and one for the error output) will be written. If omitted, all server outputs will be written to the console output streams of the java process.
* templateDir (string, optional): The template directory for model instances.  
* instanceDir (string, optional): The instance directory for model instances. The actual directories are post-fixed with the instance number. 

The wrapper resolves all arguments denoting files or directories relative to 
the working directory of the factory.

The HyPy wrapper does not use the template and instance directories, but 
some algorithms write (debug) output to the instances directories if 
existing. If this output is of interest, both directory arguments must
be specified.

#### Note on parallelisation
The HydPyOpenDABBModelWrapper may parallelize and hence run faster by distributing simulation runs onto multiple running HydPy Servers (see option 'serverInstances'). 
This however depends strongly on the chosen stochastic model, which in turn determines the call order to the underlying model. 
Especially for ensemble based algorithms (e.g. Ensemble Kalman Filter) this might result in a tremendous speed up. Advised number of server instances is the number of
simulated ensemble members (note that e.g. in case of ENKF this is the configured number plus one extra 'mean' member), which is equal to the number of 
model-instances created by OpenDA, or whole divisors of that number.
Apart from the chosen algorithm, keep in mind that also the number of physical available processors and the execution time of one single model simulation do influence the optimal number
of server instances.

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
optimised for fast communication with OpenDA.

Depending on your HyPy and [Python](https://www.python.org/) installation two additional system environment variables can be set:
* HYD_PY_PYTHON_EXE: Path to the python.exe (defaults to 'python.exe') 
* HYD_PY_SCRIPT_PATH: Path to the hyd.py script. (defaults to 'hyd.py')

With [Python](https://www.python.org/) already on your system path, you may 
not need to configure these system variables.