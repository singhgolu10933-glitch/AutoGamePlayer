package com.autogameplayer.core

interface GameModule<S, A> {

    val id: String

    val name: String

    fun initialize()

    fun snapshot(): S

    fun legalActions(state: S): List<A>

    fun execute(action: A): Boolean

    fun stop()
}
