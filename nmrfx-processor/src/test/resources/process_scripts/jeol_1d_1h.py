import os
from pyproc import *
procOpts(nprocess=7)
FID(FIDHOME+'jeol/isoCamp_PROTON-1-1.jdf')
CREATE(TMPHOME+'tst_jeol_1d_1h.nv')
acqOrder()
acqarray(0)
fixdsp(True)
skip(0)
label('1H')
acqsize(0)
tdsize(0)
sf('X_FREQ')
sw('X_SWEEP')
ref(5.0230)
DIM(1)
EXPD(lb=0.5)
ZF()
FT()
TRIM()
PHASE(ph0=-62.7,ph1=6.1,dimag=False)
AUTOREGIONS(ratio=2.691)
BCWHIT()
run()
