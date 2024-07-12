

description = "Workload trace library for OpenDC"

plugins {
    `kotlin-library-conventions`
}


val kLoggingVersion = "3.0.5"
val logBackVersion = "1.5.6"
val kotlinxCoroutinesVersion = "1.8.1"
val serializationVersion = "1.6.0"

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:$kLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logBackVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation(libs.jackson.dataformat.csv)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    implementation(project(mapOf("path" to ":opendc-trace:opendc-trace-parquet")))
    testImplementation(project(mapOf("path" to ":opendc-trace:opendc-trace-testkit")))

}
