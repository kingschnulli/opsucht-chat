package de.kingschnulli.opsuchtchat.core;

public enum ChatCategory {
    ALL("ALL"),
    PRIVATE("PN"),
    AUCTION("AUKTION"),
    SERVER("SERVER"),
    ADVERTISING("WERBUNG");

    private final String label;

    ChatCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
