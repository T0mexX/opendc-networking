plugins {
    `kotlin-library-conventions`
}


val kLoggingVersion = "3.0.5"
val logBackVersion = "1.5.6"

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:$kLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logBackVersion")

    implementation(libs.jackson.dataformat.csv)

    implementation(project(mapOf("path" to ":opendc-trace:opendc-trace-parquet")))
    testImplementation(project(mapOf("path" to ":opendc-trace:opendc-trace-testkit")))

}
