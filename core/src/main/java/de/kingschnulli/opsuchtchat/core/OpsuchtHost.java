package de.kingschnulli.opsuchtchat.core;

import java.util.Locale;

/**
 * Shared server-address check used by all client adapters.
 */
public final class OpsuchtHost {
    private OpsuchtHost() {
    }

    public static boolean matches(String address) {
        if (address == null) {
            return false;
        }

        String host = address.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            return false;
        }

        // Minecraft server list entries commonly contain host:port.
        int firstColon = host.indexOf(':');
        int lastColon = host.lastIndexOf(':');
        if (firstColon > -1 && firstColon == lastColon) {
            host = host.substring(0, firstColon);
        }

        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host.equals("opsucht.net") || host.endsWith(".opsucht.net");
    }
}
