plugins {
    java
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":backend"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
}