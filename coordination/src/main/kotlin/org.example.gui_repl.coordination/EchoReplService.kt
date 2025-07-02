package org.example.gui_repl.coordination

import java.util.concurrent.CompletableFuture

class EchoReplService {
    fun executeCommand(command: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            Thread.sleep(100)
            "Echo : $command"
        }
    }
}