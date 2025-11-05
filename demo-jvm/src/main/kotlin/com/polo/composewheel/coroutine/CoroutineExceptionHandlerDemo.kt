package com.polo.composewheel.coroutine

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

fun main(): Unit = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("Caught exception: $exception")
    }

    val scopeWithHandler = CoroutineScope(EmptyCoroutineContext + handler)

    scopeWithHandler.launch {
        println("Throwing exception from coroutine with handler")
        throw RuntimeException("Test Exception with Handler")
    }

    // give the launched coroutine time to run and the handler to print
    kotlinx.coroutines.delay(200)
}

