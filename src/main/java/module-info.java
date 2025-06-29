module org.example.gui_repl {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.gui_repl to javafx.fxml;
    exports org.example.gui_repl;
}