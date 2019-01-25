# HydPyOpenDABBModelWrapper

Allows running a [HydPy](https://github.com/hydpy-dev/hydpy) model as a 
BlackBoxModel in [OpenDA](http://openda.org/).

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

* A HydPy installation, see the [HydPy documentation](https://hydpy-dev.github.io/hydpy/).
* An OpenDA installation, see the [OpenDA documentation](http://openda.org/index.php/documentation).
* A running HydPy project, see the [LahnH example project](https://hydpy-dev.github.io/hydpy/examples.html#hydpy.core.examples.prepare_full_example_1)

### OpenDA configuration 

(ToDo: outdated, describe HydPyStochModelFactory instead of BBStochModelFactory)

OpenDA must be configured and run as usual via its main _.oda_ file.

HydPy is integrated as an OpenDA "BlackBoxModel", so the _stochModelFactory_ 
entry of the _.oda_ file needs to select the 
`org.openda.blackbox.wrapper.BBStochModelFactory` class. The rest of the 
_model.xml_ file (e.g. the `vectorSpecification`) is configured in the usual 
OpenDA way. Use the id's from the _hydpy.xml_ configuration file (see below) 
as parameters id's here.

For example:
```xml
	<stochModelFactory className="org.openda.blackbox.wrapper.BBStochModelFactory">
		<workingDirectory></workingDirectory>
		<configFile>model.xml</configFile>
	</stochModelFactory> 
```

The referred _model.xml_ file configures the BlackBoxModel. Usually, the 
_model.xml_ file refers to two additional configuration files, the
_stochModel.xml_ and _wrapper.xml_. However, the HydPy wrapper generates 
both files internally, as it retrieves all information from the HydPy 
project configuration (see below). 

To activate and configure the HydPy wrapper, the class 
`org.hydpy.openda.HydPyModelConfigFactory` has to be configured as 
`modelFactory` in the _model.xml_ file.

Example:
```xml
	<modelFactory className="org.hydpy.openda.HydPyModelConfigFactory" workingDirectory=".">
		<arg>serverPort:8080</arg>
		<arg>initializeWaitSeconds:5</arg>
		<arg>projectPath:../../hydpy_projects</arg>
		<arg>projectName:LahnH</arg>
		<arg>configFile:hydpy.xml</arg>
	</modelFactory>
```

The model factory requires the following arguments

* serverPort: The web port on which to start the HydPy server. Use any free port on your machine. 
* initializeWaitSeconds: The maximum time in seconds the wrapper implementation should wait for the HydPy server to start up. This time may depend on the actual HydPy project.
* projectPath: The path to the HydPy project directory.
* projectName: The name of the HydPy project within the project directory.
* configFile: The name of the HydPy configuration file).
* templateDir (optional): The template directory for model instances.  
* instanceDir (optional): The instance directory for model instances. The actual directories are post-fixed with the instance number. 

The wrapper resolves all arguments denoting files or directories relative to 
the working directory of the factory.

The HyPy wrapper does not use the template and instance directories, but 
some algorithms write (debug) output to the instances directories if 
existing. If this output is of interest, both directory arguments must
be specified.

### HydPy Project Configuration 
	
Part of the usual OpenDa BlackBoxModel configuration is situated in the 
HydPy project itself, as the HydPy server must know which model parameters,
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
[HydPy server](https://hydpy-dev.github.io/hydpy/servertools.html), which is 
optimised for fast communication with OpenDA.

Depending on your HyPy and [Python](https://www.python.org/) installation two additional system environment variables can be set:
* HYD_PY_PYTHON_EXE: Path to the python.exe (defaults to 'python.exe') 
* HYD_PY_SCRIPT_PATH: Path to the hyd.py script. (defaults to 'hyd.py')

With [Python](https://www.python.org/) already on your system path, you may 
not need to configure these system variables.