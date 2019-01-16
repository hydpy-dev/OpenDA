# HydPyOpenDABBModelWrapper

Allows to run a [HydPy](../../..) model as a BlackBoxModel in [OpenDA](http://openda.org/).

## Compilation

Building the sources is done via [Gradle](https://gradle.org/), usually `gradle build` should be enough to create all artifacts.

The release will be distributed under 
	_HydPyOpenDABBModelWrapper\build\distributions\HydPyOpenDABBModelWrapper.zip_

[Eclipse](https://www.eclipse.org/) is used as IDE for development and all necessary eclipse artifacts are part of the sources as well. You will need the eclipse gradle integration, [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship), as well.

## Debugging

Debugging OpenDA and the wrapper implementation from Eclipse works well, just create a _Java Application_ configuration with main class `org.openda.application.OpenDaApplication`.

All jars from an existing OpenDA installation _bin_ directory need to be included into the launch configuration. 

## Installation

In order to install the _HydPyOpenDABBModelWrapper_ into [OpenDA](http://openda.org/), simply unzip the contents of the _HydPyOpenDABBModelWrapper.zip_ into the _bin_ directory of your OpenDA-Installation.

## Usage

Please also refer to the [HydPy-OpenDA example projects](../../demos/).

### Prerequisites
* a HydPy installation, see [HydPy documentation](https://hydpy-dev.github.io/hydpy/)
* an OpenDA installation, see [OpenDA documentation](http://openda.org/index.php/documentation)
* a running HydPy project, see ???TODO???

### OpenDA configuration  

OpenDa will be configured and run in the usual way via its main _.oda_ file.

The HydPy model will be integrated as OpenDA BlackBoxModel, so the _stochModelFactory_ entry of the _.oda_ file needs to use the `org.openda.blackbox.wrapper.BBStochModelFactory` class. The rest of the _model.xml_ file (e.g. the `vectorSpecification`) is configured in the usual OpenDA way. Use the id's from the multi-run xml-configuration (see below) inside the HyPy project as parameters id's here.

For example:
```xml
	<stochModelFactory className="org.openda.blackbox.wrapper.BBStochModelFactory">
		<workingDirectory></workingDirectory>
		<configFile>model.xml</configFile>
	</stochModelFactory> 
```

The referred _model.xml_ file configures the BlackBoxModel. Usually, the _model.xml_ file refers to two additional configuration files, the _stochModel.xml_ and _wrapper.xml_.
However, in the case of this HydPy wrapper, both files will be generated internally, as all information contained will be retrieved from the HydPy project configuration (see below). 

In order to activate and configure the HydPy wrapper, the class `org.hydpy.openda.HydPyModelConfigFactory` has to be configured as `modelFactory` in the _model.xml' file.

Example:
```xml
	<modelFactory className="org.hydpy.openda.HydPyModelConfigFactory" workingDirectory=".">
		<arg>serverPort:8080</arg>
		<arg>initializeWaitSeconds:5</arg>
		<arg>projectPath:../../hydpy_projects</arg>
		<arg>projectName:LahnH</arg>
		<arg>configFile:DUD.xml</arg>
	</modelFactory>
```

The following arguments have to be configured for the model factory:
* serverPort: The web port on which the HydPy server will be started. Use any free port on your machine. 
* initializeWaitSeconds: The time in seconds the wrapper implementation will wait for the HydPy-Server to start up. This may depend on the actual HydPy project.
* projectPath: The path to the HydPy project directory, relative to the working dir of the factory.
* projectName: The name of the HydPy project within the project directory.
* configFile: The name of the HydPy multi-run configuration file, relative to the HydPy project directory. 
* templateDir (optional): the template directory for model instances, relative to the working dir of the factory.  
* instanceDir (optional): the instance directory for model instances, relative to the working dir of the factory. The actual directories will be post-fixed with the instance number. 

The template and instance directories are not used by the HyPy wrapper, but some algorithms will write (debug) output to the instances directories. If this output should be written, both directories have to be configured.

### HydPy Project Configuration 
	
Part of the usual OpenDa BlackBoxModel configuration is instead situated in the HydPy project itself, as the HydPy server must be aware which, and how, model parameters have to be manipulated by OpenDA.

The HydPy-Server will exhibit the configured parameter names, which in turn will be configured as OpenDA exchange-items by the wrapper implementation. 

Main starting point is the 'multi-run' xml file configuration. See ???TODO???.

### Running OpenDA

If everything is correctly configured, start OpenDa in the normal way, usually via `oda_run_batch.bat myOdaFile.oda`.

The HyPy wrapper will in turn start HydPy in a special server mode, which is optimized for fast communication with OpenDA.

Depending on your HyPy and [Python](https://www.python.org/) installation two additional system environment variables can be set:
* HYD_PY_PYTHON_EXE: Path to the python.exe (defaults to 'python.exe') 
* HYD_PY_SCRIPT_PATH: Path to the hyd.py script. (defaults to 'hyd.py')
If [Python](https://www.python.org/) is already on your system path, you may not need to configure these system variables.