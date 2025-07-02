import org.gradle.api.JavaVersion

plugins {
  kotlin("jvm") version "1.9.22" apply false
  id("org.openjfx.javafxplugin") version "0.0.13" apply false
  id("dev.clojurephant.clojure") version "0.8.0" apply false
  id("org.javamodularity.moduleplugin") version "1.8.12" apply false
  id("org.beryx.jlink") version "2.25.0" apply false
}

allprojects {
  group = "org.example.gui_repl"
  version = "1.0-SNAPSHOT"
  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")

  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
  }
  tasks.withType<Test>{
    useJUnitPlatform()
  }
}

