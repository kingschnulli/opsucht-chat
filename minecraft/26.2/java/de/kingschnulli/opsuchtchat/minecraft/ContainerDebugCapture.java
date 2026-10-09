package de.kingschnulli.opsuchtchat.minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.StringJoiner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Opt-in raw container snapshots used to discover OPSUCHT AH/market data.
 *
 * Enabled only together with /opschat debug and never uploaded.
 */
public final class ContainerDebugCapture {
    private final Path outputFile;
    private String lastSignature = "";

    public ContainerDebugCapture(Path outputFile) {
        this.outputFile = outputFile;
    }

    public synchronized void append(Component title, AbstractContainerMenu menu) {
        if (title == null || menu == null) {
            return;
        }

        StringJoiner signature = new StringJoiner("|");
        StringBuilder slots = new StringBuilder();
        slots.append('[');
        boolean first = true;

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String name = stack.getHoverName().getString();
            String item = stack.getItem().toString();
            String components = stack.getComponents().toString();

            signature.add(slot.index + ":" + stack.getCount() + ":" + item + ":" + name + ":" + components);

            if (!first) {
                slots.append(',');
            }
            first = false;

            slots.append("{\"slot\":").append(slot.index)
                    .append(",\"count\":").append(stack.getCount())
                    .append(",\"item\":\"").append(escape(item)).append('"')
                    .append(",\"name\":\"").append(escape(name)).append('"')
                    .append(",\"components\":\"").append(escape(components)).append("\"}");
        }
        slots.append(']');

        String currentSignature = title.getString() + "|" + signature;
        if (currentSignature.equals(lastSignature)) {
            return;
        }
        lastSignature = currentSignature;

        String line = "{\"time\":\"" + escape(Instant.now().toString())
                + "\",\"title\":\"" + escape(title.getString())
                + "\",\"container_id\":" + menu.containerId
                + ",\"slots\":" + slots
                + "}\n";

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    outputFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Debug capture must never affect gameplay.
        }
    }

    public Path outputFile() {
        return outputFile;
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
