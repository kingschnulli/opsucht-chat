package de.kingschnulli.opsuchtchat.minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Extra opt-in diagnostic stream for server-provided click/hover metadata.
 *
 * It is enabled only together with /opschat debug and never uploaded.
 */
public final class InteractionDebugCapture {
    private final Path outputFile;

    public InteractionDebugCapture(Path outputFile) {
        this.outputFile = outputFile;
    }

    public synchronized void append(GuiMessage message) {
        if (message == null) {
            return;
        }

        String fullText = message.content().getString();

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            for (Component part : message.content().toFlatList()) {
                Style style = part.getStyle();
                ClickEvent click = style.getClickEvent();
                HoverEvent hover = style.getHoverEvent();
                String span = part.getString();

                if ((click == null && hover == null) || span == null || span.isBlank()) {
                    continue;
                }

                String line = "{\"time\":\"" + escape(Instant.now().toString())
                        + "\",\"message\":\"" + escape(fullText)
                        + "\",\"span\":\"" + escape(span)
                        + "\",\"click_type\":\"" + escape(click == null ? "" : click.action().getSerializedName())
                        + "\",\"click_value\":\"" + escape(clickValue(click))
                        + "\",\"hover\":\"" + escape(hover == null ? "" : hover.toString())
                        + "\"}\n";

                Files.writeString(
                        outputFile,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException ignored) {
            // Debug output must never interfere with chat rendering.
        }
    }

    public Path outputFile() {
        return outputFile;
    }

    private static String clickValue(ClickEvent click) {
        if (click == null) {
            return "";
        }
        if (click instanceof ClickEvent.RunCommand run) {
            return run.command();
        }
        if (click instanceof ClickEvent.SuggestCommand suggest) {
            return suggest.command();
        }
        if (click instanceof ClickEvent.CopyToClipboard copy) {
            return copy.value();
        }
        if (click instanceof ClickEvent.OpenUrl openUrl) {
            return openUrl.uri().toString();
        }
        if (click instanceof ClickEvent.OpenFile openFile) {
            return openFile.path();
        }
        if (click instanceof ClickEvent.ChangePage page) {
            return Integer.toString(page.page());
        }
        return click.toString();
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
