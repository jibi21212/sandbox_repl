// ReplProcessController.java
package org.example.gui_repl.ui;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ReplProcessController {

    @FXML private TextArea outputArea;
    @FXML private TextField inputField;

    private Process replProcess;
    private PrintWriter processInputWriter;
    private BufferedReader processOutputReader; // -> Out of order or does not exist in original file
    private ExecutorService executorService;
    private volatile boolean readyForInput = false;
    /*
    Volatile keyword is something crucial for multithreaded concurrent programming.
    We essentially give a hint to the JVM that we want it to mutate this specific variable rather than
     some local variable copy, and it orders the requests to read/write it as they are called.
    It just super helpful in a multithreaded environment to get the behaviour we want in this case.
    */
    private Future<?> replOutputMonitorFuture;
    // In original, we had: private int line = 1; which we omitted in this version
    private StringBuilder currentResponse = new StringBuilder(); // Not present in original

    // Configuration for this specific REPL process
    private String replCommand = "python";
    private String[] replArgs = {"-i", "-u"}; // -u for unbuffered output -> we only had -i in original, the -u is new
    private Pattern promptPattern = Pattern.compile("^(>>> |\\.\\.\\. ).*?$"); // In original, we had: private String promptRegex = "^(>>> |\\.\\.\\. )"; instead of this

    private String tabName = "REPL-1";
    // Let SAB = `same as before` as in it's the same exactly as the original in the git repo right now
    // Let DTB = `different than before` as in it's completely new, not present at all in the original repo
    public ReplProcessController() {
        executorService = Executors.newCachedThreadPool();
    } // SAB

    @FXML
    public void initialize() {
        System.out.println("ReplProcessController initialize() called"); // This is a debugging print statement this doesn't count
        System.out.println("InputField null? " + (inputField == null)); // This as well
        outputArea.setWrapText(true); // Same
        inputField.setDisable(false);
        startReplProcess();
    }

    public void setTabName(String name) {
        this.tabName = name;
    }

    // You can add setters to allow the main controller to configure
    // replCommand, replArgs, promptRegex for each new tab
    public void setReplConfig(String command, String[] args, String promptRegex) {
        this.replCommand = command;
        this.replArgs = args;
        this.promptPattern = Pattern.compile(promptRegex);
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

            processInputWriter = new PrintWriter(replProcess.getOutputStream(), true);
            processOutputReader = new BufferedReader(new InputStreamReader(replProcess.getInputStream()));

            Platform.runLater(() -> {
                outputArea.appendText("Starting " + tabName + ": " + String.join(" ", command) + "\n");
                outputArea.appendText("Waiting for REPL to initialize... \n");
                    });

            startReplOutputMonitor();

        } catch (IOException e) {
            Platform.runLater(() -> {
                outputArea.appendText("Failed to start " + tabName + " process: " + e.getMessage() + "\n");
                inputField.setDisable(true);
            });
        }
    }

    private void startReplOutputMonitor() {
        replOutputMonitorFuture = executorService.submit(() -> {
            try {
                String line;
                AtomicBoolean firstPromptSeen = new AtomicBoolean(false);

                // Use the instance variable, not a new one!
                while ((line = processOutputReader.readLine()) != null) {
                    final String outputLine = line;
                    Platform.runLater(()->{
                        outputArea.appendText(outputLine + "\n");

                        // ADD THIS DEBUG LINE:
                        System.out.println("Checking line: '" + outputLine + "' against pattern");

                        if(promptPattern.matcher(outputLine).find()){
                            System.out.println("PROMPT FOUND!"); // ADD THIS
                            if(!firstPromptSeen.get()){
                                outputArea.appendText("REPL ready for input!\n");
                                inputField.setDisable(false);
                                firstPromptSeen.set(true);
                            }
                            readyForInput = true;
                            currentResponse.setLength(0);
                        } else {
                            currentResponse.append(outputLine).append("\n");
                        }
                    });
                }
            } catch (IOException e) {
                // ... rest of your exception handling
            }
        });
    }

    @FXML
    private void handleInput() {
        String input = inputField.getText().trim();
        inputField.clear();

        if (input.equals("clear")) {
            outputArea.clear();
            return;
        }

        if (input.equals("exit") || input.equals("quit")) {
            shutdown();
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

        readyForInput = false;
        executorService.submit(() -> sendCommandToRepl(input));
    }

    private void sendCommandToRepl(String command){
        try {
            Platform.runLater(() -> outputArea.appendText(">>> " + command + "\n"));
            processInputWriter.println(command);
            processInputWriter.flush();
            CompletableFuture<Void> responseWaiter = CompletableFuture.runAsync(()->
            {
                try{
                    for (int i = 0; i < 100; i++){
                        if(readyForInput) break;
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            });

            try {
               responseWaiter.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                Platform.runLater(() ->
                        outputArea.appendText("Command timeout or error: " + e.getMessage() + "\n")
                );
                readyForInput = true;
            }
        } catch (Exception e) {
            Platform.runLater(() ->
                    outputArea.appendText("Error sending command: " + e.getMessage() + "\n")
            );
            readyForInput = true;
        }
    }

    public void shutdown() {
        readyForInput = false;

        if (replOutputMonitorFuture != null && !replOutputMonitorFuture.isDone()) {
            replOutputMonitorFuture.cancel(true);
        }

        if (processInputWriter != null) {
            processInputWriter.close();
        }

        if (replProcess != null && replProcess.isAlive()) {
            replProcess.destroy();
            try {
                if (!replProcess.waitFor(3, TimeUnit.SECONDS)) {
                    replProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                replProcess.destroyForcibly();
            }
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}