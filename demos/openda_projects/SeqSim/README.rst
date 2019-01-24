
.. _`OpenDA`: https://www.openda.org/
.. _`HydPy server`: https://hydpy-dev.github.io/hydpy/servertools.html#hydpy.exe.servertools.HydPyServer
.. _`HydPy`: https://github.com/hydpy-dev/hydpy
.. _`calibration example`: ../DUD
.. _`HydPyOpenDABBModelWrapper`: ./../../../extensions/HydPyOpenDABBModelWrapper
.. _`HydPy main class`: https://hydpy-dev.github.io/hydpy/hydpytools.html#hydpy.core.hydpytools.HydPy
.. _`hydpy.xml`: hydpy.xml
.. _`Alpha`: https://hydpy-dev.github.io/hydpy/hland.html#hydpy.models.hland
.. _`main.oda`: main.oda
.. _`DUD`: https://www.jstor.org/stable/1268154?seq=1#page_scan_tab_contents

Perform a single sequential run
-------------------------------

Here, "sequential run" means, that `OpenDA`_ tells the `HydPy server`_ to
simulate the first initialised day only, then simulate the second day,
and so on.  `OpenDA`_ does not change any parameter or condition values
between successive simulation runs.  Hence, the final simulation results
must be identical to one covering the whole initialisation period right
away.  Of course, this is not a real life example on coupling of `OpenDA`_
and `HydPy`_, but it serves well for the educational and testing purposes.

When new to using `HydPy`_ or `OpenDA`_, we suggest to start with the the
`calibration example`_.

As suggested above, we want to compare the results of a complete simulation
run (which we will perform within a Python process) with a sequential
run (controlled by `OpenDA`_ and the `HydPyOpenDABBModelWrapper`_.  We
first prepare a working instance of the `HydPy main class`_:

>>> import os
>>> os.chdir('../../hydpy_projects')
>>> from hydpy import HydPy, pub, print_values, run_subprocess
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6
>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1997-01-01', '1d'
>>> hp.prepare_everything()

So far, everythings corresponds to the calibration example, except we are
focussing on the first Lahn subcatchment this time.  The value of parameter
`Alpha`_ of subcatchment Lahn 1 equals 2.0, ass defined within the
configuration file `hydpy.xml`,.  Hence, we do have to set this value
within the Python process, too, before performing the simulation run:

>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)
>>> hp.doit()

We save the complete simulated discharge series for later comparisons:

>>> sim_internal = hp.nodes.lahn_1.sequences.sim.series
>>> print_values(sim_internal[:5])
17.342381, 9.544265, 7.548786, 7.096891, 6.805922
>>> os.chdir('../openda_projects/SeqSim')

The `OpenDA`_ configuration of this example is very much similar to the
one of the `calibration example`_.  One notable difference is, that instead
`main.oda`_ selects the `SequentialSimulation` algorithm instead of `DUD`_.
The command to execute `OpenDA`_ is also identical and the way to load
the results into Python is also identical:

>>> run_subprocess('oda_run_batch main.oda', verbose=False)
>>> import runpy
>>> results = runpy.run_path('results/final.py')
>>> sim_external = results['pred_a_central'][:, 0]

As to be expected, are the results of the `OpenDA`_ based sequential run
identical to the ones gained above.  This is not only true for the first
five values, but also for the complete discharge series of one year
within a precision of six decimal places:

>>> print_values(sim_external[:5])
17.342381, 9.544265, 7.548786, 7.096891, 6.805922
>>> import numpy
>>> numpy.all(numpy.round(sim_internal, 6) == numpy.round(sim_external, 6))
True
