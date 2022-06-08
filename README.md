
# Optimising HydPy simulations with OpenDA

<img src="logo.png" align="right" width="350">

[![Travis (.com)](https://img.shields.io/travis/com/hydpy-dev/OpenDA/master)](https://app.travis-ci.com/github/hydpy-dev/OpenDA/branches)
[![GitHub all releases](https://img.shields.io/github/downloads/hydpy-dev/OpenDA/total)](https://github.com//hydpy-dev/OpenDA/releases)
[![GitHub License](https://img.shields.io/github/license/hydpy-dev/OpenDA?color=blue)](https://github.com/hydpy-dev/OpenDA/blob/master/LICENSE)

[![GitHub Open Issues](https://img.shields.io/github/issues-raw/hydpy-dev/OpenDA)](https://github.com//hydpy-dev/OpenDA/issues?q=is%3Aopen+is%3Aissue)
[![GitHub Closed Issues](https://img.shields.io/github/issues-closed-raw/hydpy-dev/OpenDA?label=closed)](https://github.com/hydpy-dev/OpenDA/issues?q=is%3Aissue+is%3Aclosed)

This repository provides an extension to the open data assimilation framework [OpenDA](http://openda.org/) that integrates 
[HydPy](https://github.com/hydpy-dev/hydpy) in form of a [wrapper](extensions/HydPyOpenDABBModelWrapper) via OpenDA's black box model concept.

[OpenDA](http://openda.org/) comes with various numerical algorithms for 
calibrating model parameters and improving simulations via data assimilation.  
The design of the [wrapper](extensions/HydPyOpenDABBModelWrapper) is 
sufficiently general to apply these algorithms on all hydrological models 
implemented into [HydPy](https://github.com/hydpy-dev/hydpy).

For a quick overview, the extension provides the following main components:
* a [wrapper](extensions/HydPyOpenDABBModelWrapper#readme) for [HydPy](https://github.com/hydpy-dev/hydpy) suitable to be used as an [OpenDA](http://openda.org/) black box model
* an [IDataObject implementation](extensions/HydPyOpenDABBModelWrapper#HyPyIoObject) that can be used to access obervation data directly from a running [HydPy](https://github.com/hydpy-dev/hydpy) instance
* a specialized [noise model implementation](extensions/HydPyOpenDABBModelWrapper/SpatialNoiseModel.md) that correlates [HydPy](https://github.com/hydpy-dev/hydpy) model elements/HRUs by their spatial distribution
* some [example projects](demos) that demonstrate the useage of this extension

For a deeper understanding of the possibilities and limitations of the [wrapper](extensions/HydPyOpenDABBModelWrapper),
see the documentation on [OpenDA](http://openda.org/) and 
[HydPy](https://github.com/hydpy-dev/hydpy) (especially [HydPy server](https://hydpy-dev.github.io/hydpy/master/servertools.html), as 
well as the current [OpenDA issues](https://github.com/hydpy-dev/OpenDA/issues)
and [HydPy issues](https://github.com/hydpy-dev/hydpy/issues) related to [OpenDA](http://openda.org/).

The [wrapper](extensions/HydPyOpenDABBModelWrapper) was implemented
by [Björnsen Consulting Engineers](https://www.bjoernsen.de/index.php?id=bjoernsen&L=2))
on behalf of the [German Federal Institute of Hydrology](https://www.bafg.de/EN/Home/homepage_en_node.html) 
(BfG).

Please refer to the release notes of each individual release for which version of the [wrapper](extensions/HydPyOpenDABBModelWrapper) works 
with which version of [HydPy](https://github.com/hydpy-dev/hydpy).
