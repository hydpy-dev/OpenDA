# HydPyOpenDABBModelWrapper

Allows to integrate [HydPy](../../..) as a BlackBoxModel into [OpenDA](http://openda.org/)

## Compilation

The build is based on [Gradle](https://gradle.org/), so usually `gradle build` should be enough to create all artifacts.

The release will be distributed under 
	_HydPyOpenDABBModelWrapper\build\distributions\HydPyOpenDABBModelWrapper.zip_

[Eclipse](https://www.eclipse.org/) is used as IDE for development and all necessary eclipse artifacts are part of the sources as well. You will need the eclipse gradle integration, [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship), as well.

## Debugging

Debugging from Eclipse works well, just create a _Java Application_ configuration with main class `org.openda.application.OpenDaApplication'`.

All jars from an existing OpenDA installation _bin_ directory need to be included into the launch. 

## Installation

In order to install the _HydPyOpenDABBModelWrapper_ into [OpenDA](http://openda.org/), simply unzip the contents of the _HydPyOpenDABBModelWrapper.zip_ into the _bin_ directory of your OpenDA-Installation.

## Usage

Please refer to the [HydPy-OpenDA example projects](../../demos/).

### Prerequisites
* a HydPy installation, see [HydPy documentation](https://hydpy-dev.github.io/hydpy/)
* an OpenDA installation, see [OpenDA documentation](http://openda.org/index.php/documentation)
* a running HydPy Project, see ???

### OpenDA configuration  

OpenDa will be configured an run in the usual way via its main _.oda_ file.

The HydPy model will be integrated as OpenDA BlackBoxModel, so the _stochModelFactory_ entry of the _.oda_ file needs to use the `org.openda.blackbox.wrapper.BBStochModelFactory` class.

For example:
```xml
	<stochModelFactory className="org.openda.blackbox.wrapper.BBStochModelFactory">
		<workingDirectory></workingDirectory>
		<configFile>model.xml</configFile>
	</stochModelFactory> 
```

The referred _model.xml_ file configures the BlackBoxModel. Usually, the _model.xml_ file refers to two additional configuration file, the _stochModel.xml_ and _modelWrapper.xml_.
However, in the case of this HydPy-Wrapper, both files will be generated internally, as all information contained will be retrieved from the HydPy-Project configuration (see below). 

In order to activate and configure the HydPy-Wrapper, the class `org.hydpy.openda.HydPyModelConfigFactory` has to be configured as `modelFactory` in the _model.xml' file.

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

The following arguments have to be configured:
* serverPort: The web port on which the HydPy-Server will be started. Use any free port on your machine. 
* initializeWaitSeconds: The time in seconds the wrapper implementation will wait for the HydPy-Server to start up. This may depend on the actual HydPy project.
* projectPath: The path to the HydPy project directory, relative to the working dir of the _model_xml_.
* projectName: The name of the HydPy project within the project directory.
* configFile: The name of the HydPy multi-run configuration file, relative to the given project directory (see below). 
* templateDir (optional): the template directory for model instances, relative to the _model.xml_ working dir  
* instanceDir (optional): the instance directory for model instances, relative to the _model.xml_ working dir. The actual directories will be post-fixed with the instance number. 

Usually the template and instance directories not used by the HyPyWrapper, but some algorithms will write some (debug) output to the instances dirs. If this output should be written, both directories have to be configured.

### HydPy Project Configuration 
	
Part of the usual OpenDa Black-Box-Model configuration is instead situated in the HydPy project itself, as the HydPy server must be aware which, and how, model parameters have to be manipulated by OpenDA.

The HydPy-Server will exhibit the configured parameter names, which in turn will be configured as OpenDA-exchange-item by the wrapper implementation. 

Main starting point is the 'multi-run' xml file configuration. See ???TODO???.  

### Running OpenDA
TODO
* normal way: TODO
* additional environment variables:
  	HYD_PY_EXE
	HYD_PY_SCRIPT_PATH

