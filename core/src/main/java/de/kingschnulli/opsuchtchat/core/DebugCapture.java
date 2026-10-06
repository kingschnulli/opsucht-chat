package de.kingschnulli.opsuchtchat.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Explicit opt-in local debug capture. Nothing is transmitted anywhere.
 */
public final class DebugCapture {
    private final Path outputFile;
    private boolean enabled;

    public DebugCapture(Path outputFile) {
        this.outputFile = outputFile;
    }

    public synchronized boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public Path outputFile() {
        return outputFile;
    }

    public synchronized void append(ChatEnvelope message, Classification classification) {
        if (!enabled) {
            return;
        }

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    outputFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write("{\"time\":\"");
                writer.write(escape(Instant.now().toString()));
                writer.write("\",\"source\":\"");
                writer.write(message.source().name());
                writer.write("\",\"category\":\"");
                writer.write(classification.category().name());
                writer.write("\",\"rule\":\"");
                writer.write(escape(classification.ruleId()));
                writer.write("\",\"text\":\"");
                writer.write(escape(message.text()));
                writer.write("\"}\n");
            }
        } catch (IOException ignored) {
            // Debug capture must never break chat rendering.
        }
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
