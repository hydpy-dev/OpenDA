
.. _`DUD`: https://www.jstor.org/stable/1268154?seq=1#page_scan_tab_contents
.. _`HydPy`: https://github.com/hydpy-dev/hydpy
.. _`OpenDA`: https://www.openda.org/
.. _`Alpha`: https://hydpy-dev.github.io/hydpy/hland.html#hydpy.models.hland.hland_control.Alpha
.. _`hland_v1`: https://hydpy-dev.github.io/hydpy/hland_v1.html
.. _`LahnH`: https://hydpy-dev.github.io/hydpy/examples.html#hydpy.core.examples.prepare_full_example_1
.. _`observation file`: data/dill.discharge.noos
.. _`HydPy projects`: ../../hydpy_projects
.. _`HydPy main class`: https://hydpy-dev.github.io/hydpy/hydpytools.html#hydpy.core.hydpytools.HydPy
.. _`module pub`: https://hydpy-dev.github.io/hydpy/pubtools.html#hydpy.core.pubtools.Pub
.. _`print_values`: https://hydpy-dev.github.io/hydpy/objecttools.html#hydpy.core.objecttools.print_values
.. _`run_subprocess`: https://hydpy-dev.github.io/hydpy/commandtools.html#hydpy.exe.commandtools.run_subprocess
.. _`printprogress`: https://hydpy-dev.github.io/hydpy/optiontools.html#hydpy.core.optiontools.Options.printprogress
.. _`reprdigits`: https://hydpy-dev.github.io/hydpy/optiontools.html#hydpy.core.optiontools.Options.reprdigits
.. _`main.oda`: main.oda
.. _`HydPyOpenDABBModelWrapper`: ./../../../extensions/HydPyOpenDABBModelWrapper
.. _`HydPy server`: https://hydpy-dev.github.io/hydpy/servertools.html#hydpy.exe.servertools.HydPyServer
.. _`model.xml`: model.xml
.. _`GetItem`: https://hydpy-dev.github.io/hydpy/itemtools.html#hydpy.core.itemtools.GetItem
.. _`hydpy.xml`: hydpy.xml
.. _`SetItem`: https://hydpy-dev.github.io/hydpy/itemtools.html#hydpy.core.itemtools.SetItem
.. _`HydPyConfigMultipleRuns.xsd`: https://github.com/hydpy-dev/hydpy/blob/master/hydpy/conf/HydPyConfigMultipleRuns.xsd
.. _`xmltools`: https://hydpy-dev.github.io/hydpy/xmltools.html
.. _`servertools`: https://hydpy-dev.github.io/hydpy/servertools.html
.. _`runpy`: https://docs.python.org/library/runpy.html

Calibrating model parameters with DUD
-------------------------------------

`DUD`_ stands for "doesn't use derivates".  It is - as the name suggests -
a derivative-free optimisation algorithm.  This example shows how to use its
`OpenDA`_ implementation for calibrating parameters of `HydPy`_ models.
To keep the configuration as simple as possible, we calibrate a single
model parameter, the nonlinearity parameter `Alpha`_ of the *HydPy* model
`hland_v1`_, affecting the generation of direct discharge, within a single
headwater catchment of the `LahnH`_ example project.

Prepare the artificial data
...........................

This is an artificial data example.  To prove that `DUD`_ finds the
"true" value of `Alpha`_, we simulate a "true" discharge time series
with this value of `Alpha`_ beforehand.  Afterwards, we start a `DUD`_
run with another `Alpha`_ value, using the previously simulated discharge
as "artificial observations".

You can generate this (or a similar) discharge time series by yourself by
executing the following commands in your Python console.  However, this is
not necessary to run `DUD`_, as the resulting `observation file`_ is already
available.

First, we go into the `HydPy projects`_ directory, containing the *LahnH*
example project:

>>> import os
>>> os.chdir('../../hydpy_projects')

Second, we import the necessary `HydPy`_ tools (the `HydPy main class`_, the
`module pub`_, and functions `print_values`_ and `run_subprocess`_) and set
options `printprogress`_ and `reprdigits`_ to our favour:

>>> from hydpy import HydPy, pub, print_values, run_subprocess
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

Third, we initialise the project in the default way for 1996.  The value
of parameter `Alpha`_ is 1.0 for subcatchment Dill, which is our "true"
value in the following:

>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1997-01-01', '1d'
>>> hp.prepare_everything()
>>> hp.elements.land_dill.model.parameters.control.alpha
alpha(1.0)

Forth, we perform a simulation run and show the first five discharge
values simulated for the outlet of the Dill catchment, serving as the
"true" discharge values in the following:

>>> hp.doit()
>>> true_discharge = hp.nodes.dill.sequences.sim.series
>>> print_values(true_discharge[:5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751

Fifth, we write the "true" discharge into `observation file`_
*dill.discharge.noos*:

>>> filepath = '../openda_projects/DUD/data/dill.discharge.noos'
>>> with open(filepath, 'w') as noosfile:
...     _ = noosfile.write('# TimeZone:GMT+1\n')
...     for date, discharge in zip(pub.timegrids.init, true_discharge):
...         date = date + '1d'
...         line = f'{date.datetime.strftime("%Y%m%d%H%M%S")}   {discharge}\n'
...         _ = noosfile.write(line)

The resulting file agrees with the "NOOS" format, which is one of many options.
See the `OpenDA`_ documentation on the format specifications and the other
available formats of time series files.

Sixths, we go back to the original working directory, to finally start
applying `DUD`_:

>>> os.chdir('../openda_projects/DUD')


Calibrate parameter Alpha
.........................

To start `OpenDA`_, one has to write the following command into a command
line tool:

>>> command = 'oda_run_batch main.oda'

From within a Python process, one can use function `run_subprocess`_ instead:

>>> run_subprocess(command, verbose=False)

`oda_run_batch` is a batch script available in your `OpenDA`_ installation.
Its path must either be added to the environment variable *PATH* or prefixed
to the filename.

`main_oda`_ is the entry point for `OpenDA`_, selecting the
`NoosTimeSeriesObserver` (see above), the `DUD`_ algorithm, and the
`PythonResultWriter`.  Using the `HydPyStochModelFactory` ensures that
the `HydPyOpenDABBModelWrapper`_ starts and controls the `HydPy server`_.

`model.xml`_ configures the `HydPyOpenDABBModelWrapper`_.  Besides the
*ModelFactory* specifications (see subsection *OpenDA configuration*) the
*vectorSpecification* must be defined.  Here, we define two
exchange items, the first one changing model parameter `Alpha`_ (we also
named the exchange item *alpha*, but other names would do as well), the
second one querying the simulated discharge (the name *dill_nodes_sim_series*
is mandatory here, see the documentation on class `GetItem`_ for further
information).

`hydpy.xml`_ specifies the configuration of the initialised `LahnH`_ project
as well as the required exchange items, corresponding to the exchange
items defined in `model.xml`_.  You see that exchange item *alpha* is a
`SetItem`_, which assigns values given by `DUD`_ to parameter `Alpha`_
without any modification.  Additionally, there is the `GetItem`_ *sim.series*,
which sends the discharge simulated for the outlet of catchment Dill
to `DUD`_.  `hydpy.xml`_ must agree with the appropriate version of XML
schema file `HydPyConfigMultipleRuns.xsd`_.  See the documentation on
modules `xmltools`_ and `servertools`_ for further information.

The temporary and final results of the `DUD`_ algorithm are available in
the subfolder *results*.  Due to selecting the *PythonResultWriter*, we
can load the final results quickly with the help of module `runpy`_:

>>> import runpy
>>> results = runpy.run_path('results/final.py')

`model.xml`_ sets the initial value of the exchange item *alpha* to 2.0.
After initialisation, the `HydPyOpenDABBModelWrapper`_ queries this
value and sends it back to the `HydPy Server`_, which also sets the
value of parameter `Alpha`_ to 2.0.  The following command prints
all evaluated *alpha* values.  `DUD`_ starts at 2.0 and reaches
the correct value of 1.0 with a precision of six decimal places within
six simulation runs:

>>> print_values(2.0+results['evaluatedParameters'][:,0])
2.0, 3.0, 1.012238, 1.001496, 1.000002, 1.0

The following commands print the "artificial observations", the simulation
results of the first evaluation (*alpha*=2.0), and the simulation results
of the last evaluation (*alpha*=1.0), respectively:

>>> print_values(results['observed'][-1, :5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751
>>> print_values(results['predicted'][0, :5])
35.250827, 7.774062, 5.035808, 4.513706, 4.251594
>>> print_values(results['predicted'][-1, :5])
11.658511, 8.842278, 7.103614, 6.00763, 5.313751

At least for this minimal example, `DUD`_ works well for calibrating
`HydPy`_ models.
