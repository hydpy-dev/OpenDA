# Changes since v0.21.0

Added new option _suppressInternalStateSaving_ to configuration of _SpatialNoiseModelFactory_ which allows to
suppress saving/restoring the internal state of the noise model. This will allow to use it in combination with
the ParticleFilter. 