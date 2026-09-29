package com.autogameplayer.core

interface DecisionPolicy<S, A> {

    fun choose(
        state: S,
        actions: List<A>
    ): A?
}
