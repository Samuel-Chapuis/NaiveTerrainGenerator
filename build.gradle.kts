plugins {
    // Apply the Kotlin JVM plugin. Replace the version with the one you need.
    kotlin("jvm") version "1.8.0"
    // The application plugin adds support for building and running a CLI application.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use the Kotlin standard library.
    implementation(kotlin("stdlib"))
    // You can also add testing libraries if needed.
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.0")
}

application {
    // Specify the main class for your application.
    // If your file is named Main.kt, Kotlin compiles it to a class called MainKt.
    mainClass.set("MainKt")
}
