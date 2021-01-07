plugins {
    java
    kotlin("jvm") version "1.4.21"
    id("com.hivemq.extension") version "1.0.0"
}

group = "com.ncr"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.hivemq:hivemq-mqtt-client:1.2.1")
    implementation("com.typesafe:config:1.4.1")
}

hivemqExtension {
    name = "Message Forwarder"
    author = "NCR"
    priority = 0
    startPriority = 1000
    mainClass = "com.ncr.mqtt.ExtensionMain"
    sdkVersion = "4.4.4"
}

// To run locally, uncomment the following block and set value to path to extracted hivemq
//tasks.prepareHivemqHome {
//    hivemqFolder.set("c:/apps/hivemq-4.4.4")
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
    }
}

