



Perform an ensemble of sequential runs
......................................


>>> import os
>>> os.chdir('../../hydpy_projects')

>>> from hydpy import HydPy, pub, print_values
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

>>> hp = HydPy('LahnH')
>>> pub.timegrids = '1996-01-01', '1996-02-10', '1d'
>>> hp.prepare_everything()
>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)
>>> hp.doit()

>>> os.chdir('../openda_projects/SeqEnsSim')

>>> import runpy, subprocess
>>> _ = subprocess.run('oda_run_batch main.oda > temp.txt', shell=True)
>>> results = runpy.run_path('results/final.py')

>>> ext_sims = {}
>>> for idx in range(1, 4):
...     ext_sims[idx] = results[f'pred_f_{idx-1}'][:, 0]
...     print(f'worker {idx}:', end=' ')
...     print_values(ext_sims[idx][:5])
worker 1: 19.794427, 8.985969, 7.372045, 7.048461, 6.737002
worker 2: 14.577521, 10.222372, 7.794734, 7.142355, 6.880434
worker 3: 16.842996, 9.663802, 7.589468, 7.103641, 6.819666

>>> import numpy
>>> ext_alphas = {}
>>> for idx in range(1, 4):
...     with open(f'results/instance{idx}/ArmaNoiseModel-log.txt') as logfile:
...         alphas = [2.0+float(noise) for noise in logfile.readlines()[3::2]]
...         ext_alphas[idx] = alphas
...     print(f'worker {idx}:', end=' ')
...     print_values(alphas[:5])
worker 1: 2.220929, 2.032679, 2.004834, 2.000715, 2.000106
worker 2: 1.728765, 1.95988, 1.994066, 1.999122, 1.99987
worker 3: 1.953494, 1.993121, 1.998982, 1.999849, 1.999978

>>> int_sims = {}
>>> for idx, alphas in ext_alphas.items():
...     hp.reset_conditions()
...     pub.timegrids.sim.firstdate = '1996-01-01'
...     pub.timegrids.sim.lastdate = '1996-01-02'
...     for alpha in alphas:
...         model = hp.elements.land_lahn_1.model
...         model.parameters.control.alpha(alpha)
...         hp.doit()
...         pub.timegrids.sim.firstdate += '1d'
...         pub.timegrids.sim.lastdate += '1d'
...     int_sims[idx] = hp.nodes.lahn_1.sequences.sim.series.copy()
...     print(f'worker {idx}:', end=' ')
...     print_values(int_sims[idx][:5])
worker 1: 19.794427, 8.985969, 7.372045, 7.048461, 6.737002
worker 2: 14.577521, 10.222372, 7.794734, 7.142355, 6.880434
worker 3: 16.842996, 9.663802, 7.589468, 7.103641, 6.819666

>>> for idx in range(1, 4):
...     print(f'worker {idx}:', end=' ')
...     numpy.all(
...         numpy.round(int_sims[idx], 6) == numpy.round(ext_sims[idx], 6))
worker 1: True
worker 2: True
worker 3: True
