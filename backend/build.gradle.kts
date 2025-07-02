plugins {
    java
    id("dev.clojurephant.clojure")
}

repositories {
    mavenCentral()
    maven("https://repo.clojars.org/")
}

dependencies {
    implementation(project(":common"))

    implementation("org.clojure:clojure:1.11.1")
    implementation("org.clojure:core.async:1.6.681")
}