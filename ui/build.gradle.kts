plugins {
    java
    application
    id("org.javamodularity.moduleplugin")
    id("org.openjfx.javafxplugin")
    id("org.beryx.jlink")
}

dependencies {
    implementation(project(":common"))
    // implementation(project(":coordination")) NOT FINISHED YET
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

application {
    mainModule.set("org.example.gui_repl.ui")
    mainClass.set("org.example.gui_repl.ui.ReplApplication")
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml")
}
