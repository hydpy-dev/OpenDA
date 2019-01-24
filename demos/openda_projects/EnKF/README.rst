
.. _`multiple sequential runs`: ../SeqEnsSim
.. _`OpenDA`: https://www.openda.org/
.. _`calibration example`: ../DUD
.. _`DUD`: https://www.jstor.org/stable/1268154?seq=1#page_scan_tab_contents
.. _`Alpha`: https://hydpy-dev.github.io/hydpy/hland.html#hydpy.models.hland
.. _`LZ`: https://hydpy-dev.github.io/hydpy/hland.html#hydpy.models.hland.hland_states.LZ
.. _`LahnH`: https://hydpy-dev.github.io/hydpy/examples.html#hydpy.core.examples.prepare_full_example_1
.. _`main.oda`: main.oda
.. _`algorithm.xml`: algorithm.xml
.. _`model.xml`: model.xml
.. _`hydpy.xml`: hydpy.xml

Adjust model states with the Ensemble Kalman Filter
---------------------------------------------------

This example extends the `multiple sequential runs` example.  Now `OpenDA`_
does not only perturb some model properties randomly but does so to improve
simulations.  There is also a similarity to the
`calibration example`_, in so far that both are artificial data experiments.
In the `calibration example`_, we checked the `DUD`_ algorithm being able
to find the `Alpha`_ value we knew to be the "true" one.  In this example,
we instead distort the model state `LZ`_ during a simulation run and check
the Ensemble Kalman Filter to be able to adjust another simulation run
to this distortion.

Prepare the artificial data
...........................

The procedure to prepare the `LahnH`_ project equals the ones of the
previous examples:

>>> import os
>>> os.chdir('../../hydpy_projects')
>>> from hydpy import HydPy, pub, print_values, run_subprocess
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6
>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1996-02-10', '1d'
>>> hp.prepare_everything()

We now perform two consecutive simulation runs, the first one throughout
the first five initialised days:

>>> element = hp.elements.land_lahn_1
>>> node = hp.nodes.lahn_1
>>> pub.timegrids.sim.lastdate = '1996-01-06'
>>> hp.doit()

We save the current model state for later:

>>> conditions = hp.conditions

Before continuing the simulation, we add 4 mm to the base flow storage `LZ`_:

>>> element.model.sequences.states.lz += 4.0
>>> pub.timegrids.sim.firstdate = '1996-01-06'
>>> pub.timegrids.sim.lastdate = '1996-02-10'
>>> hp.doit()

The modified series of `LZ`_ looks as follows:

>>> print_values(element.model.sequences.states.lz.series[:10])
8.276894, 8.361623, 8.441877, 8.517635, 8.589283, 12.113043,
11.612774, 11.187676, 10.725625, 10.282656

When inspecting the discharge simulated at the catchment outlet, which
we take as the "true" discharge in the following, one sees
an abrupt increase due to the sudden increase of groundwater:

>>> sim_true = node.sequences.sim.series.copy()
>>> print_values(sim_true[:10])
9.621296, 8.503069, 7.774927, 7.34503, 7.15879, 10.026952, 9.612801,
9.260914, 8.878438, 8.511759

We now reset the previously saved model states (with the unmodified
value of `LZ`_) and recalculate the discharge series, taken as the
"uncorrected" simulation result:

>>> hp.conditions = conditions
>>> hp.doit()
>>> sim_uncorrected = node.sequences.sim.series.copy()
>>> print_values(sim_uncorrected[:10])
9.621296, 8.503069, 7.774927, 7.34503, 7.15879, 6.852588, 6.569539,
6.343338, 6.081358, 5.830198

The Ensemble Kalman Filter needs to know (an estimate of) the "true"
discharge to improve the model states.  As in the `calibration example`_,
we write the "true" series into a *NOOS*  file:

>>> filepath = '../openda_projects/EnKF/data/lahn_1.discharge.noos'
>>> with open(filepath, 'w') as noosfile:
...     _ = noosfile.write('# TimeZone:GMT+1\n')
...     for date, discharge in zip(pub.timegrids.init, sim_true):
...         date = date + '1d'
...         line = f'{date.datetime.strftime("%Y%m%d%H%M%S")}   {discharge}\n'
...         _ = noosfile.write(line)
>>> os.chdir('../openda_projects/EnKF')

Adjust state LZ
...............

The `OpenDA`_ configuration resembles the configuration of the
`multiple sequential runs`_ example.  Notable differences are that file
`main.oda`_ selects the Ensemble Kalman Filter algorithm, file
`algorithm.xml`_ enables not only `stochInit` but also `stochForcing` for
stepwise state perturbations, and files `model.xml`_ and `hydpy.xml`_ define
an exchange item for changing the value of state `LZ`_ instead of parameter
`Alpha`_.  Using 20 ensemble members and the AR(1) model for generating
the stochastic perturbations, the Ensemble Kalman Filter returns the
following corrected discharge series:

>>> run_subprocess('oda_run_batch main.oda', verbose=False)
>>> import runpy
>>> results = runpy.run_path('results/final.py')
>>> sim_corrected = results['pred_f'][:, 0]

At the end of the simulation period, the simulated discharge corrected
by the Ensemble Kalman Filter approximates the "true" discharge more
closely than the uncorrected discharge:

>>> print_values(sim_uncorrected[-7:])
2.118697, 2.031194, 1.947306, 1.866882, 1.78978, 1.715862, 1.644997
>>> print_values(sim_corrected[-7:])
2.859875, 2.838286, 2.662556, 2.574135, 2.529551, 2.462996, 2.477771
>>> print_values(sim_true[-7:])
3.093177, 2.965429, 2.842957, 2.725542, 2.612978, 2.505062, 2.401603
