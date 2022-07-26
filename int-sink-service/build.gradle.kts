/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("idlab.obelisk.java-conventions")
}

dependencies {
    implementation(project(":lib-service-utils"))
    implementation(project(":lib-pulsar-utils"))
    implementation(project(":plugin-datastore-clickhouse"))
    testImplementation("io.vertx:vertx-junit5-rx-java2:4.2.5")
}

description = "int-sink-service"
