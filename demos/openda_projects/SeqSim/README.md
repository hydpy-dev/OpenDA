

Perform a single sequential run
...............................


>>> import os
>>> os.chdir('../../hydpy_projects')

>>> from hydpy import HydPy, pub, print_values
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1997-01-01', '1d'
>>> hp.prepare_everything()
>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)
>>> hp.doit()

>>> sim_internal = hp.nodes.lahn_1.sequences.sim.series
>>> print_values(sim_internal[:5])
17.342381, 9.544265, 7.548786, 7.096891, 6.805922

>>> filepath = '../openda_projects/SeqSim/data/lahn_1.discharge.noos'
>>> with open(filepath, 'w') as noosfile:
...     _ = noosfile.write('# TimeZone:GMT+1\n')
...     for date, discharge in zip(pub.timegrids.init, sim_internal):
...         date = date + '1d'
...         line = f'{date.datetime.strftime("%Y%m%d%H%M%S")}   {discharge}\n'
...         _ = noosfile.write(line)

>>> os.chdir('../openda_projects/SeqSim')

>>> import runpy, subprocess
>>> call = 'oda_run_batch main.oda > temp.txt'
>>> _ = subprocess.run(call, shell=True)

>>> with open('results/final.py') as file_:
...     text = file_.read()
>>> text = text.replace('NaN', 'np.nan')
>>> with open('results/final.py', 'w') as file_:
...     _ = file_.write(text)

>>> results = runpy.run_path('results/final.py')
>>> sim_external = results['pred_a_central'][:, 0]
>>> print_values(sim_external[:5])
17.342381, 9.544265, 7.548786, 7.096891, 6.805922

>>> import numpy
>>> numpy.all(numpy.round(sim_internal, 6) == numpy.round(sim_external, 6))
True
