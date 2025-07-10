module org.example.gui_repl.ui {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.example.gui_repl.common;
    requires java.management;
    // requires org.example.gui_repl.coordination; NOT FINISHED YET
    opens org.example.gui_repl.ui to javafx.fxml;
    exports org.example.gui_repl.ui;
}