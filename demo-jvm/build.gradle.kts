plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

application {
    // Top-level `main` in file CoroutineExceptionHandlerDemo.kt compiles to CoroutineExceptionHandlerDemoKt
    mainClass.set("com.polo.composewheel.coroutine.CoroutineExceptionHandlerDemoKt")
}
