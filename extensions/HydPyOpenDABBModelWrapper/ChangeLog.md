# Changes since v0.22.0

Introduced new configuration parameters in the hydpy configuration file to allow to set python-exe and hydpy script paths.
Made process termination a bit more robust, especially in the case where server startup fails directly.
Explicitly declare time zone of firstdate/lastdate in hydpy.xml, else the demos wont run on computers with a different system time.
Changed hydPy dependency to 5.0.0
