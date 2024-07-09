plugins {
    `kotlin-library-conventions`
}
dependencies {

    implementation(libs.jackson.dataformat.csv)

    implementation(project(mapOf("path" to ":opendc-trace:opendc-trace-parquet")))
    testImplementation(project(mapOf("path" to ":opendc-trace:opendc-trace-testkit")))
}
