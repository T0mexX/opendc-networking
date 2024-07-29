/*
 * Copyright (c) 2021 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

description = "Library for simulating datacenter network components"

plugins {
    `kotlin-library-conventions`
    `testing-conventions`
    `jacoco-conventions`
    kotlin("plugin.serialization") version "1.9.22"
//    id("org.jetbrains.kotlinx.atomicfu") version "0.25.0"
}

val kLoggingVersion = "3.0.5"
val logBackVersion = "1.5.6"
val kotestVersion = "5.9.1"
val kotestDataTestVersion = kotestVersion
val kotestPropertyTestVersion = kotestVersion
val serializationVersion = "1.6.0"

dependencies {
    implementation(projects.opendcTrace.opendcTraceApiNew)
    implementation(libs.progressbar)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation(projects.opendcSimulator.opendcSimulatorCore)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$kLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logBackVersion")
    implementation(libs.clikt)

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$version")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestDataTestVersion")
    testImplementation("io.kotest:kotest-property:$kotestPropertyTestVersion")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
