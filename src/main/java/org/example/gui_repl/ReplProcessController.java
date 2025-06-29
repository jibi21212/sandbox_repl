// ReplProcessController.java
package org.example.gui_repl;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReplProcessController {

    @FXML private TextArea outputArea;
    @FXML private TextField inputField;

    private Process replProcess;
    private PrintWriter processInputWriter;
    private ExecutorService executorService;
    private volatile boolean readyForInput = true;
    private ConcurrentLinkedQueue<String> replOutputQueue = new ConcurrentLinkedQueue<>();
    private Future<?> replOutputMonitorFuture;
    private int line = 1;

    // Configuration for this specific REPL process
    private String replCommand = "python";
    private String[] replArgs = {"-i"};
    private String promptRegex = "^(>>> |\\.\\.\\. )";

    private String tabName = "REPL-1"; // Will be set by parent controller

    // Constructor (called when FXML is loaded, but can be customized later)
    public ReplProcessController() {
        executorService = Executors.newCachedThreadPool();
    }

    @FXML
    public void initialize() {
        outputArea.setWrapText(true);
        startReplProcess();
        startReplOutputMonitor();
    }

    public void setTabName(String name) {
        this.tabName = name;
    }

    // You can add setters to allow the main controller to configure
    // replCommand, replArgs, promptRegex for each new tab
    public void setReplConfig(String command, String[] args, String prompt) {
        this.replCommand = command;
        this.replArgs = args;
        this.promptRegex = prompt;
    }

    private void startReplProcess() {
        try {
            List<String> command = new ArrayList<>();
            command.add(replCommand);
            for (String arg : replArgs) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            replProcess = pb.start();

            processInputWriter = new PrintWriter(replProcess.getOutputStream());

            outputArea.appendText("Started " + tabName + ": " + String.join(" ", command) + "\n");
            outputArea.appendText("Waiting for initial prompt...\n");

        } catch (IOException e) {
            Platform.runLater(() -> {
                outputArea.appendText("Failed to start " + tabName + " process: " + e.getMessage() + "\n");
                inputField.setDisable(true);
            });
        }
    }

    private void startReplOutputMonitor() {
        replOutputMonitorFuture = executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(replProcess.getInputStream()))) {
                String line;
                Pattern p = Pattern.compile(promptRegex);
                while ((line = reader.readLine()) != null) {
                    final String outputLine = line;
                    replOutputQueue.offer(outputLine);

                    Platform.runLater(() -> {
                        Matcher m = p.matcher(outputLine.trim());
                        if (m.matches()) {
                            readyForInput = true;
                            while(!replOutputQueue.isEmpty()) {
                                outputArea.appendText(replOutputQueue.poll() + "\n");
                            }
                        } else {
                            if (readyForInput) {
                                outputArea.appendText(outputLine + "\n");
                            }
                        }
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> outputArea.appendText(tabName + " Output Error: " + e.getMessage() + "\n"));
            } finally {
                Platform.runLater(() -> {
                    outputArea.appendText("\n--- " + tabName + " Process Exited ---\n");
                    inputField.setDisable(true);
                    readyForInput = false;
                });
            }
        });
    }

    @FXML
    private void handleInput() {
        String input = inputField.getText();
        inputField.clear();

        if (input.equals("clear")) {
            outputArea.clear();
            return;
        }

        if (!readyForInput) {
            outputArea.appendText(tabName + " not ready for input. Please wait.\n");
            return;
        }

        if (replProcess == null || !replProcess.isAlive()) {
            outputArea.appendText(tabName + " process is not running.\n");
            return;
        }

        outputArea.appendText(line + ": >> " + input + "\n");
        line++;
        executorService.submit(() -> {
            processInputWriter.println(input);
            processInputWriter.flush();
            readyForInput = false;
            // You might need a more sophisticated prompt detection or
            // timeout if the REPL doesn't always provide a prompt.
        });

        // --- Custom Command Interception (e.g., "fork()") ---
        // This is a simple example. For a real REPL, you'd parse the input more carefully.
        if (input.trim().equals("fork()")) {
            Platform.runLater(() -> {
                // How to call the main controller to create a new tab?
                // This ReplProcessController doesn't know about the TabPane.
                // It needs a callback or a reference to the main controller.
                // We'll address this in the main ReplController.
                outputArea.appendText("Attempting to fork new REPL process...\n");
            });
        }
    }

    public void shutdown() {
        if (replOutputMonitorFuture != null && !replOutputMonitorFuture.isDone()) {
            replOutputMonitorFuture.cancel(true);
        }
        if (replProcess != null && replProcess.isAlive()) {
            replProcess.destroy();
            try {
                replProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (replProcess.isAlive()) {
                replProcess.destroyForcibly();
            }
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}