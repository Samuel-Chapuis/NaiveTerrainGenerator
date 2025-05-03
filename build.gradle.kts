plugins {
    kotlin("jvm") version "1.8.0"
    application
}

repositories {
    mavenCentral()
    // Pour récupérer jMCX directement depuis GitHub via JitPack
    maven { url = uri("https://jitpack.io") }                    // :contentReference[oaicite:0]{index=0}
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.github.jglrxavpok.hephaistos:common:2.6.1")
    // Optional: the JSON (Gson) layer, same version
    implementation("io.github.jglrxavpok.hephaistos:gson:2.6.1")

}

application {
    // Votre class principale
    mainClass.set("MainKt")
}
