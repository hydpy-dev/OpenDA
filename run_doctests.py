# -*- coding: utf-8 -*-
"""Collect all `README.md` files and execute their doctests."""
import doctest
import os
import sys
import numpy

homedir = os.path.abspath('.')
counter = numpy.zeros(2, dtype=int)

for root, _, filenames in os.walk('.'):
    filenames = [fn for fn in filenames if fn in ('README.md', 'README.rst')]
    for filename in filenames:
        path = os.path.join(root, filename)
        print(path)
        os.chdir(root)
        counter += numpy.array(doctest.testfile(filename))
        os.chdir(homedir)

at_least_one_test_failed = bool(counter[0])
sys.exit(at_least_one_test_failed)

