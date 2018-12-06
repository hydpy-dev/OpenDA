# -*- coding: utf-8 -*-

from hydpy.models.hland_v1 import *

controlcheck(projectdir="LahnH", controldir="default")

ic(0.96158999999999994, 1.4615899999999999, 0.96218999999999999,
   1.4621900000000001, 0.96277999999999997, 1.46278, 0.96335999999999999,
   1.46336, 0.96392999999999995, 1.46393)
sp(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
wc(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
sm(138.31396000000001, 135.71124, 147.54968, 145.47141999999999,
   154.96404999999999, 153.32804999999999, 160.91917000000001,
   159.62433999999999, 165.65575000000001, 164.63255000000001)
uz(4.42062)
lz(10.14007)
quh(0.7, 0.0)
