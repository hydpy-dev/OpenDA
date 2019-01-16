# HydPyOpenDABBModelWrapper

Allows to integrate [HydPy](../../..) as a BlackBoxModel into [OpenDA](http://openda.org/)

## Compilation

The build is based on [Gradle](https://gradle.org/), so usually `gradle build` should be enough to create all artifacts.

The release will be distributed under 
	_HydPyOpenDABBModelWrapper\build\distributions\HydPyOpenDABBModelWrapper.zip_

[Eclipse](https://www.eclipse.org/) is used as IDE for development and all necessary eclipse artifacts are part of the sources as well. You will need the eclipse gradle integration, [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship) as well.

## Debugging

Debugging from Eclipse works well, just create a _Java Application_ configuration with main class `org.openda.application.OpenDaApplication'`.

All jars from an existing OpenDA installation _bin_ directory need to be included into the launch. 

## Installation

In order to install the _HydPyOpenDABBModelWrapper_ into [OpenDA](http://openda.org/), simply unzip the contents of the _HydPyOpenDABBModelWrapper.zip_ into the _bin_ directory of your OpenDA-Installation.

## Usage

TODO