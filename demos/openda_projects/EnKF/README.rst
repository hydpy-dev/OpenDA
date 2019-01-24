
Adjust parameters dynamically with the Ensemble Kalman Filter
-------------------------------------------------------------

Prepare artificial data
.......................

>>> import os
>>> os.chdir('../../hydpy_projects')

>>> from hydpy import HydPy, pub, print_values
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1996-02-10', '1d'
>>> hp.prepare_everything()

>>> element = hp.elements.land_lahn_1
>>> node = hp.nodes.lahn_1
>>> pub.timegrids.sim.lastdate = '1996-01-06'
>>> hp.doit()
>>> conditions = hp.conditions
>>> element.model.sequences.states.lz += 4.0
>>> pub.timegrids.sim.firstdate = '1996-01-06'
>>> pub.timegrids.sim.lastdate = '1996-02-10'
>>> hp.doit()

>>> print_values(element.model.sequences.states.lz.series[:10])
8.276894, 8.361623, 8.441877, 8.517635, 8.589283, 12.113043,
11.612774, 11.187676, 10.725625, 10.282656
    
>>> true_discharge = node.sequences.sim.series.copy()

>>> import numpy
>>> numpy.savetxt('orig_lz.txt', element.model.sequences.states.lz.series)
>>> numpy.savetxt('orig_q.txt', node.sequences.sim.series)

>>> filepath = '../openda_projects/EnKF/data/lahn_1.discharge.noos'
>>> with open(filepath, 'w') as noosfile:
...     _ = noosfile.write('# TimeZone:GMT+1\n')
...     for date, discharge in zip(pub.timegrids.init, true_discharge):
...         date = date + '1d'
...         line = f'{date.datetime.strftime("%Y%m%d%H%M%S")}   {discharge}\n'
...         _ = noosfile.write(line)

>>> hp.conditions = conditions
>>> hp.doit()
>>> predicted_discharge = node.sequences.sim.series.copy()

Assimilate
----------

>>> os.chdir('../openda_projects/EnKF')

>>> import runpy, subprocess
>>> _ = subprocess.run('oda_run_batch main.oda > temp.txt', shell=True)
>>> results = runpy.run_path('results/final.py')

>>> filtered_discharge = results['pred_f'][:, 0]

>>> print_values(true_discharge[-7:])
3.093177, 2.965429, 2.842957, 2.725542, 2.612978, 2.505062, 2.401603
>>> print_values(predicted_discharge[-7:])
2.118697, 2.031194, 1.947306, 1.866882, 1.78978, 1.715862, 1.644997
>>> print_values(filtered_discharge[-7:])
2.859875, 2.838286, 2.662556, 2.574135, 2.529551, 2.462996, 2.477771