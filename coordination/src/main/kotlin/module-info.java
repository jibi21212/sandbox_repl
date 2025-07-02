module org.example.gui_repl.coordination {
    requires kotlin.stdlib;

    opens org.example.gui_repl.coordination to javafx.fxml;
    exports org.example.gui_repl.coordination;
}