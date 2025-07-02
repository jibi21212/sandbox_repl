package org.example.gui_repl.ui;// Main.java (or ReplApplication.java)
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ReplApplication extends Application {

    private ReplController mainController; // To manage the main window's global aspects

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("repl-view.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800); // Larger initial size
        mainController = loader.getController();

        primaryStage.setTitle("Dynamic REPL Environment");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Ensure proper shutdown
        primaryStage.setOnHidden(e -> mainController.shutdown());
    }

    public static void main(String[] args) {
        launch(args);
    }
}