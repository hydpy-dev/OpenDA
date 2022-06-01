# Changes since v0.20.0

Introduced new configuration for 'stateConditions' that will be written on every simulation run; these stateConditions will be automatically the 'internal' state of a hyd py instance.
Started to implement support for restart files and saving/restoring the 'internal' state.
Especially restoring the internal state is implemented by letting HydPy instances load and apply the above mentioned stateConditions.

Introduced new hack for item id's. for HydPy item ids that contain ".shared", it will be assumed that the initial value retreived from HydPy is identical for all instances.
The wrapper will fetch theme only once, will will improve performance for very large data sets. Typical use cases are
- items that are used as observations
- items that are initially nan and will only be later filled by the simulation 

Moved developer related stuff to end of documentation.

Fixed the `EnKF` example, so that it works with the newest version of the adapter.  (And adjust the `AEnKF` example a little.)
Updated the `LahnH` example project: `musk_classic` replaced `musk_v001`.

Fixed: setting seriesreader dir did not work.
