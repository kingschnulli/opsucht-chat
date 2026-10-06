package de.kingschnulli.opsuchtchat.core.server;

import de.kingschnulli.opsuchtchat.core.server.opsucht.OpsuchtServerAdapter;

public final class ServerAdapterRegistry {
    private ServerAdapterRegistry() {
    }

    public static ChatServerAdapter resolve(String address) {
        ChatServerAdapter opsucht = new OpsuchtServerAdapter();
        if (opsucht.matchesAddress(address)) {
            return opsucht;
        }
        return null;
    }
}
