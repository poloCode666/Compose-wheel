package com.polo.composewheel.coroutine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(DelicateCoroutinesApi::class)
fun main(): Unit = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("coroutine handler exception: $exception")
    }
    val job1 = Job()
    val coroutineScope = CoroutineScope(Dispatchers.IO + handler+job1)
    val emptyContext = GlobalScope





    val job = emptyContext.launch() {
        launch {
            delay(3000L)


            println("jobB finish")
            println("jobB isActive $isActive")

        }

       launch {
            delay(2000L)

            println("jobA finish")
            println("jobA isActive $isActive")

        }
    }







    delay(1000L)
    job.cancel()
    println("coroutine json cancel")




    // give the launched coroutine time to run and the handler to print
    delay(5000L)
}

