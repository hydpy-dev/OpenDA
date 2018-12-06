

Perform a single sequential run
...............................


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

>>> import runpy, subprocess
>>> process = subprocess.Popen(
...     'hyd.py start_server 8080 LahnH 1996-01-01 1997-01-01 1d',
...     shell=True)
>>> _ = subprocess.run('hyd.py await_server 8080 10', shell=True)

>>> os.chdir('../openda_projects/SeqSim')


>>> sim_internal = hp.nodes.lahn_1.sequences.sim.series[:5]
>>> print_values(sim_internal)
17.342381, 9.544265, 7.548786, 7.096891, 6.805922

>>> call = 'oda_run_batch main.oda > temp.txt'
>>> _ = subprocess.run(call, shell=True)
>>> results = runpy.run_path('results/final.py')
>>> sim_external = results['pred_a_central'][1:6, 0]
>>> print_values(sim_external)
17.342381, 9.544265, 7.548786, 7.096891, 6.805922

>>> import numpy
>>> numpy.all(numpy.round(sim_internal, 6) == numpy.round(sim_external, 6))
True

>>> from urllib import request
>>> _ = request.urlopen('http://localhost:8080/close_server')
>>> process.kill()
>>> _ = process.communicate()