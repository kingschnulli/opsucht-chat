package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpsuchtHostTest {
    @Test
    void acceptsOpsuchtDomainsAndPorts() {
        assertTrue(OpsuchtHost.matches("opsucht.net"));
        assertTrue(OpsuchtHost.matches("java.opsucht.net"));
        assertTrue(OpsuchtHost.matches("JAVA.OPSUCHT.NET:25565"));
        assertTrue(OpsuchtHost.matches("opsucht.net."));
    }

    @Test
    void rejectsLookalikeAndOtherServers() {
        assertFalse(OpsuchtHost.matches("notopsucht.net"));
        assertFalse(OpsuchtHost.matches("opsucht.net.example.org"));
        assertFalse(OpsuchtHost.matches("example.org"));
        assertFalse(OpsuchtHost.matches(null));
    }
}
