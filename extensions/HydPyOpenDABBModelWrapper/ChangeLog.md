# Changes since v0.18.0

Introduced the (provisional) possibility to split up Timeseries1D items (delivered by HydPY) into multiple exchange items (communicated to OpenDA).
This happens for Timeseries1D items with ending '.split'.
The item will be split up into exchange items with id <itemId>.<itemname> where itemname is the name provided by HydPy via GET_itemnames.

Using the name HydPy server calls 'GET_deregister_internalconditions' and 'GET_update_outputitemvalues'.

The initial state (all item values) fetched from HydPy is now shared between all ensemble instances, assuming HydPy provides always the same values.
It is internally prepared, that later this might get configured per item.  