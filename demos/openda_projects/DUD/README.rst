
.. _`DUD`: https://www.jstor.org/stable/1268154?seq=1#page_scan_tab_contents
.. _`HydPy`: https://github.com/hydpy-dev/hydpy
.. _`OpenDA`: https://www.openda.org/
.. _`Alpha`: https://hydpy-dev.github.io/hydpy/hland.html#hydpy.models.hland.hland_control.Alpha
.. _`hland_v1`: https://hydpy-dev.github.io/hydpy/hland_v1.html
.. _`LahnH`: https://hydpy-dev.github.io/hydpy/examples.html#hydpy.core.examples.prepare_full_example_1
.. _`observation file`: data/dill.discharge.noos

Calibrating model parameters with DUD
-------------------------------------

`DUD`_ stands for "doesn't use derivates".  It is a - as the name suggests -
derivative-free optimisation algorithm.  This example shows how to use its
`OpenDA`_ implementation for calibrating parameters of `HydPy`_ models.
To keep the configuration as simple as possible, we calibrate a single
model parameter, the nonlinearity parameter `Alpha`_ of the *HydPy* model
`hland_v1`_, affecting the generation of direct discharge, within a single
headwater catchment of the `LahnH`_ example project.

Prepare artificial data
.......................

This is an artificial data example.  In order to proof the `DUD`_ actually
finds the "true" value of `Alpha`_, we simulate a "true" discharge time
series this this value of `Alpha`_.  Afterwards, we start a `DUD`_ run
with another `Alpha`_ value, using the previously simulated discharge as
"artificial observations".

You can generate this (or a similar) discharge time series by yourself by
executing the following commands in your Python console.  However, this is
not necessary to run `DUD`_, as the resulting `observation file`_ is already
available.

>>> import os
>>> os.chdir('../../hydpy_projects')

>>> from hydpy import HydPy, pub, print_values
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

>>> hp = HydPy('LahnH')

>>> pub.timegrids = '1996-01-01', '1997-01-01', '1d'

>>> hp.prepare_network()
>>> hp.init_models()
>>> hp.load_conditions()
>>> hp.prepare_inputseries()
>>> hp.prepare_simseries()
>>> hp.load_inputseries()

>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)

>>> hp.doit()

>>> hp.elements.land_dill.model.parameters.control.alpha
alpha(1.0)
>>> true_discharge = hp.nodes.dill.sequences.sim.series
>>> print_values(true_discharge[:5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751

>>> filepath = '../openda_projects/DUD/data/dill.discharge.noos'
>>> with open(filepath, 'w') as noosfile:
...     _ = noosfile.write('# TimeZone:GMT+1\n')
...     for date, discharge in zip(pub.timegrids.init, true_discharge):
...         date = date + '1d'
...         line = f'{date.datetime.strftime("%Y%m%d%H%M%S")}   {discharge}\n'
...         _ = noosfile.write(line)

Calibrate
---------

>>> import runpy, subprocess
>>> os.chdir('../openda_projects/DUD')

>>> _ = subprocess.run('oda_run_batch main.oda > temp.txt', shell=True)
>>> results = runpy.run_path('results/final.py')

>>> print_values(results['observed'][-1, :5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751

>>> print_values(2.0+results['evaluatedParameters'][:,0])
2.0, 3.0, 1.012238, 1.001496, 1.000002, 1.0

>>> print_values(results['predicted'][0, :5])
35.250827, 7.774062, 5.035808, 4.513706, 4.251594

>>> print_values(results['predicted'][-1, :5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751
