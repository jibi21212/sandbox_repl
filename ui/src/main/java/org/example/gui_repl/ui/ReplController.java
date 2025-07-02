// ReplController.java
package org.example.gui_repl.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplController {

    @FXML private TabPane replTabPane;
    @FXML private VBox detailsSidebar;
    @FXML private StackPane sidebarContentPane; // Where dynamic content goes

    private AtomicInteger replCounter = new AtomicInteger(0);
    private Map<String, ReplProcessController> activeReplControllers = new HashMap<>(); // To manage all controllers
    private ExecutorService executorService;

    // Inject this controller into a new ReplProcessController if needed (for fork)
    // You'd need a way for ReplProcessController to call back to this one
    // to request a new tab.

    @FXML
    public void initialize() {

        detailsSidebar.setVisible(false); // Hide sidebar initially
        detailsSidebar.setManaged(false); // Don't take up space when hidden

        createNewReplTab(); // Open an initial REPL tab on startup
        executorService = Executors.newCachedThreadPool();
    }

    @FXML
    private void createNewReplTab() {
        System.out.println("createNewReplTab method called.");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("repl-tab-content.fxml"));
            System.out.println("FXMLLoader for tab content created. Resource: " + getClass().getResource("repl-tab-content.fxml"));

            // CHANGE THIS LINE: Cast to SplitPane, or use a more general Node/Parent type
            SplitPane tabContent = loader.load(); // <--- CHANGE THE TYPE HERE
            System.out.println("Tab content loaded from FXML.");

            ReplProcessController newController = loader.getController();
            System.out.println("ReplProcessController instance obtained: " + newController);

            String tabName = "REPL-" + replCounter.incrementAndGet();
            newController.setTabName(tabName);
            System.out.println("New tab name set: " + tabName);

            Tab newTab = new Tab(tabName, tabContent); // This line is fine because Tab takes any Node
            newTab.setOnClosed(event -> {
                System.out.println("Tab " + tabName + " is closing. Shutting down its process.");
                newController.shutdown();
                activeReplControllers.remove(tabName);
            });
            System.out.println("New Tab object created.");

            replTabPane.getTabs().add(newTab);
            System.out.println("Tab added to TabPane.");
            replTabPane.getSelectionModel().select(newTab);
            System.out.println("New tab selected.");
            activeReplControllers.put(tabName, newController);
            System.out.println("Active REPL controller mapped.");
        } catch (IOException e) {
            System.err.println("Error creating new REPL tab: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error during tab creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Sidebar Toggle Buttons ---

    @FXML
    private void toggleProcessesList() {
        // Here you would dynamically load a list view/table for processes
        // and populate it by running a system command (e.g., 'ps -eo pid,comm')
        // OR by asking each active ReplProcessController for its own threads/processes.
        updateSidebar("Processes");
    }

    @FXML
    private void toggleThreadsList() {
        // Similar to processes, but focusing on threads.
        // Again, either system-wide or from individual REPL processes.
        updateSidebar("Threads");
    }

    @FXML
    private void toggleSocketsList() {
        // This is where you'd execute 'netstat' or similar, parse its output,
        // and populate a list of clickable socket items.
        updateSidebar("Sockets");
    }

    @FXML
    private void toggleUIList() {
        // What do you want to show for UI? JavaFX Scene Graph inspector?
        // This is complex, could be a placeholder or a simple list of active windows/nodes.
        updateSidebar("UI");
    }

    private void updateSidebar(String category) {
        sidebarContentPane.getChildren().clear(); // Clear previous content

        // Show/hide sidebar
        boolean isVisible = !detailsSidebar.isVisible() || !detailsSidebar.getProperties().getOrDefault("currentCategory", "").equals(category);
        detailsSidebar.setVisible(isVisible);
        detailsSidebar.setManaged(isVisible);
        detailsSidebar.getProperties().put("currentCategory", isVisible ? category : "");


        if (isVisible) {
            // Add a label as a placeholder
            Label title = new Label("Active " + category);
            title.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-padding: 5px;");
            VBox categoryContent = new VBox(title);
            categoryContent.setSpacing(5);

            // --- Dynamic Content Generation ---
            // This is where the actual logic for populating the list goes.
            // For now, let's just add some dummy items.

            if (category.equals("Sockets")) {
                // Execute netstat as a separate Java process (easier to get clean output)
                executorService.submit(() -> { // Use the main controller's executor
                    try {
                        Process netstatProcess = new ProcessBuilder("netstat", "-tulnp").start();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(netstatProcess.getInputStream()));
                        List<String> rawSocketLines = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            rawSocketLines.add(line);
                        }
                        netstatProcess.waitFor();

                        Platform.runLater(() -> {
                            // Replace placeholder with actual list
                            VBox socketList = new VBox(new Label("Listening/Established Sockets:"));
                            socketList.setSpacing(2);
                            if (rawSocketLines.isEmpty()) {
                                socketList.getChildren().add(new Label("No sockets detected."));
                            } else {
                                // Parse and add clickable items
                                for (String sLine : rawSocketLines) {
                                    // Basic netstat parsing (needs robust regex for production)
                                    if (sLine.matches("^(tcp|udp).*?(LISTEN|ESTABLISHED).*?(\\S+):(\\d+).*")) {
                                        Label socketLabel = new Label(sLine.trim());
                                        socketLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px; -fx-padding: 2px;");
                                        socketLabel.setWrapText(true);
                                        socketLabel.setOnMouseClicked(e -> showSocketDetails(sLine)); // Click handler
                                        socketLabel.getStyleClass().add("sidebar-item"); // For styling
                                        socketList.getChildren().add(socketLabel);
                                    }
                                }
                            }
                            sidebarContentPane.getChildren().setAll(socketList);
                        });
                    } catch (IOException | InterruptedException e) {
                        Platform.runLater(() -> sidebarContentPane.getChildren().setAll(new Label("Error getting socket info: " + e.getMessage())));
                    }
                });
            } else if (category.equals("Processes")) {
                // Similar execution of 'ps' command
                sidebarContentPane.getChildren().setAll(new Label("Processes will be listed here."));
            } else if (category.equals("Threads")) {
                // For threads, you'd likely want to list threads *within* the currently active REPL tab
                // Or a system-wide view (harder).
                sidebarContentPane.getChildren().setAll(new Label("Threads will be listed here."));
            } else if (category.equals("UI")) {
                sidebarContentPane.getChildren().setAll(new Label("UI specific details here."));
            }
            categoryContent.getChildren().add(sidebarContentPane); // Add the actual content pane
            sidebarContentPane.getChildren().setAll(new Label("Loading " + category + " info...")); // Show loading state
            detailsSidebar.getChildren().setAll(categoryContent); // Set the sidebar content
        }
    }

    private void showSocketDetails(String socketInfo) {
        // This is where you'd open a new pane/tab/dialog
        // to show live messages for the selected socket.
        // For a true live feed, you'd need to instrument the REPL's socket
        // operations to send data back to JavaFX.
        System.out.println("Displaying details for socket: " + socketInfo); // For debugging
        // Example: Create a new TextArea to show messages
        TextArea socketMonitorArea = new TextArea("Monitoring socket: " + socketInfo + "\n");
        socketMonitorArea.setEditable(false);
        socketMonitorArea.setPrefHeight(200); // Give it some height
        socketMonitorArea.setWrapText(true);

        // Replace sidebar content with the monitor area
        sidebarContentPane.getChildren().setAll(socketMonitorArea);

        // --- The HARD PART: Live Socket Monitoring ---
        // To get live messages, you need:
        // 1. A way for your REPL language (e.g., Python) to "report" socket events back to Java.
        //    - This could be by printing specially formatted messages that Java parses.
        //    - Or, if you control the REPL's "socket" objects (e.g., if you wrap Python's socket),
        //      you could make them call back to Java directly via JNI or JNA, or a custom IPC.
        // 2. A background thread in Java listening for these reports and updating `socketMonitorArea`.
        // This is highly language-specific and complex.
    }


    // Inside ReplController.java
    public void shutdown() {
        // Shutdown all active REPL processes (already present)
        activeReplControllers.values().forEach(ReplProcessController::shutdown);

        // Shut down this controller's own executor service
        if (executorService != null) {
            executorService.shutdownNow(); // Attempt to stop all running tasks
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("Main ReplController executor did not terminate in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                System.err.println("Interrupted while waiting for main ReplController executor to shut down.");
            }
        }
    }
}