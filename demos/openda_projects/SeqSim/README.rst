
.. _`OpenDA`: https://www.openda.org/
.. _`HydPy server`: https://hydpy-dev.github.io/hydpy/master/servertools.html#hydpy.exe.servertools.HydPyServer
.. _`HydPy`: https://github.com/hydpy-dev/hydpy
.. _`calibration example`: ../DUD
.. _`HydPyOpenDABBModelWrapper`: ./../../../extensions/HydPyOpenDABBModelWrapper
.. _`HydPy main class`: https://hydpy-dev.github.io/hydpy/master/hydpytools.html#hydpy.core.hydpytools.HydPy
.. _`hydpy.xml`: hydpy.xml
.. _`Alpha`: https://hydpy-dev.github.io/hydpy/master/hland.html#hydpy.models.hland
.. _`main.oda`: main.oda
.. _`DUD`: https://www.jstor.org/stable/1268154?seq=1#page_scan_tab_contents

Perform a single sequential run
-------------------------------

Here, "sequential run" means, that `OpenDA`_ tells the `HydPy server`_ to simulate the
first initialised day only, then simulate the second day, and so on.  `OpenDA`_ does not
change any parameter or condition values between successive simulation runs.  Hence, the
final simulation results must be identical to one covering the whole initialisation
period right away.  Of course, this is not a real-life example of the coupling of
`OpenDA`_ and `HydPy`_, but it serves well for educational and testing purposes.

When new to using `HydPy`_ or `OpenDA`_, we suggest starting with the
`calibration example`_.

As suggested above, we want to compare the results of a complete simulation run
(performed within a Python process) with a sequential run (controlled by `OpenDA`_ and
the `HydPyOpenDABBModelWrapper`_).  We first prepare a working instance of the
`HydPy main class`_:

>>> import os
>>> os.chdir("../../hydpy_projects")
>>> from hydpy import HydPy, pub, print_values, run_subprocess
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 4
>>> hp = HydPy("LahnH")
>>> pub.timegrids = "1996-01-01", "1997-01-01", "1d"
>>> hp.prepare_everything()

So far, everything corresponds to the calibration example, except we are focussing on
the headwater "Lahn 1" this time.  The value of parameter `Alpha`_ of subcatchment Lahn
1 equals 2.0, as defined within the configuration file `hydpy.xml`_.  Hence, we must
also set this value within the Python process, before performing the simulation run:

>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)
>>> hp.simulate()

We save the complete simulated discharge series for later comparisons:

>>> sim_internal = hp.nodes.lahn_1.sequences.sim.series
>>> print_values(sim_internal[:5])
17.4565, 9.5591, 7.5482, 7.091, 6.8009
>>> os.chdir("../openda_projects/SeqSim")

The `OpenDA`_ configuration of this example is very much similar to the one of the
`calibration example`_.  One notable difference is that the `main.oda`_ file selects the
`SequentialSimulation` algorithm instead of `DUD`_.  The command to execute `OpenDA`_
and the way to load the results into Python are also identical:

>>> _ = run_subprocess("oda_run_batch main.oda", verbose=False)
>>> import runpy
>>> results = runpy.run_path("results/final.py")
>>> sim_external = results["pred_a_central"][:, 0]

As to be expected, are the results of the `OpenDA`_ based sequential run identical to
the ones gained above, which is not only true for the first five values but also the
complete discharge series of one year within a precision of six decimal places:

>>> print_values(sim_external[:5])
17.4565, 9.5591, 7.5482, 7.091, 6.8009
>>> import numpy
>>> numpy.all(numpy.round(sim_internal, 6) == numpy.round(sim_external, 6))
True
