# -*- coding: utf-8 -*-
"""Collect all `README.md` and `README.rst` files and execute their doctests."""
import doctest
import os
import sys
import numpy
from matplotlib import pyplot

counter = numpy.zeros(2, dtype=int)
for root, filenames in tuple((os.path.abspath(r), f) for r, _, f in os.walk(".")):
    for filename in (fn for fn in filenames if fn in ("README.md", "README.rst")):
        filepath = os.path.join(root, filename)
        os.chdir(root)
        counter += numpy.array(doctest.testfile(filepath))
        pyplot.close()  # ensure different README files don't share figure instances
at_least_one_test_failed = bool(counter[0])
sys.exit(at_least_one_test_failed)
