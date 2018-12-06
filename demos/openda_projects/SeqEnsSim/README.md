



Perform an ensemble of sequential runs
......................................


>>> import os
>>> os.chdir('../../hydpy_projects')

>>> from hydpy import HydPy, pub, print_values
>>> pub.options.printprogress = False
>>> pub.options.reprdigits = 6

>>> hp = HydPy('LahnH')

>>> pub.timegrids = '1996-01-01', '1997-01-01', '1d'
>>> pub.sequencemanager.inputfiletype = 'nc'

>>> hp.prepare_network()
>>> hp.init_models()
>>> hp.load_conditions()
>>> hp.prepare_inputseries()
>>> hp.prepare_simseries()

>>> pub.sequencemanager.open_netcdf_reader(
...     flatten=True, isolate=True, timeaxis=0)
>>> hp.load_inputseries()
>>> pub.sequencemanager.close_netcdf_reader()

>>> hp.elements.land_lahn_1.model.parameters.control.alpha(2.0)

>>> hp.doit()

>>> import runpy, subprocess

>>> for filename in os.listdir('.'):
...     if filename.endswith('.log'):
...         os.remove(filename)
>>> process = subprocess.Popen(
...     'hyd.py start_server 8080 LahnH 1996-01-01 1997-01-01 1d',
...     shell=True)
>>> from hydpy import print_latest_logfile
>>> print_latest_logfile(wait=10.0)
<BLANKLINE>
>>> os.chdir('../openda_projects/SeqEnsSim')




>>> call = 'oda_run_batch main.oda > temp.txt'
>>> _ = subprocess.run(call, shell=True)

>>> ext_sims = {}
>>> results = runpy.run_path('results/final.py')
>>> for idx in range(1, 4):
...     ext_sims[idx] = results[f'pred_f_{idx-1}'][1:, 0]
...     print(f'worker {idx}:', end=' ')
...     print_values(ext_sims[idx][:5])
worker 1: 19.794427, 9.413089, 7.37961, 7.025074, 6.720015
worker 2: 14.577521, 9.424086, 7.723374, 7.166622, 6.916414
worker 3: 16.842996, 9.545965, 7.583282, 7.106425, 6.824685

>>> import numpy
>>> ext_alphas = {}
>>> for idx in range(1, 4):
...     with open(f'results/temp{idx}/ArmaNoiseModel-log.txt') as logfile:
...         deltas = [float(noise) for noise in logfile.readlines()[3::2]]
...         alphas = 2.0 + numpy.cumsum(deltas)
...         ext_alphas[idx] = alphas
...     print(f'worker {idx}:', end=' ')
...     print_values(alphas[:5])
worker 1: 2.220929, 2.253608, 2.258441, 2.259156, 2.259262
worker 2: 1.728765, 1.688646, 1.682711, 1.681833, 1.681704
worker 3: 1.953494, 1.946615, 1.945598, 1.945447, 1.945425

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
...     int_sims[idx] = hp.nodes.lahn_1.sequences.sim.series[:-1].copy()
...     print(f'worker {idx}:', end=' ')
...     print_values(int_sims[idx][:5])
worker 1: 19.794427, 9.413089, 7.37961, 7.025074, 6.720015
worker 2: 14.577521, 9.424086, 7.723374, 7.166622, 6.916414
worker 3: 16.842996, 9.545965, 7.583282, 7.106425, 6.824685

>>> for idx in range(1, 4):
...     print(f'worker {idx}:', end=' ')
...     numpy.all(numpy.round(int_sims[idx], 6) == numpy.round(ext_sims[idx], 6))
...     numpy.savetxt(f'int_sim_{idx}.log', int_sims[idx])
...     numpy.savetxt(f'ext_sim_{idx}.log', ext_sims[idx])
worker 1: True
worker 2: True
worker 3: True
    
>>> from urllib import request
>>> _ = request.urlopen('http://localhost:8080/close_server')
>>> process.kill()
>>> _ = process.communicate()