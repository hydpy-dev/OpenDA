

Calibrate with DUD
------------------

Prepare artificial data
.......................

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

>>> with open('../openda_projects/DUD/data/series/dill.discharge.noos', 'w') as noosfile:
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
