package de.kingschnulli.opsuchtchat.core.presentation;

public record TextRange(int start, int end) {
    public TextRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid text range: " + start + ".." + end);
        }
    }
}
