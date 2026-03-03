package com.dark.tool_neuron.repo.ums

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal fun <T, R> StateFlow<List<T>>.mapFlow(transform: (List<T>) -> List<R>): Flow<List<R>> =
    map(transform)
