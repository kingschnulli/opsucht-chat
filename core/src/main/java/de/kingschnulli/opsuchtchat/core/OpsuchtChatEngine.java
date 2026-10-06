package de.kingschnulli.opsuchtchat.core;

import de.kingschnulli.opsuchtchat.core.server.opsucht.OpsuchtServerAdapter;

/**
 * Compatibility wrapper kept while the public package is still young.
 * New code should use ChatEngine with a ChatServerAdapter.
 */
@Deprecated
public final class OpsuchtChatEngine extends ChatEngine {
    public OpsuchtChatEngine() {
        super(new OpsuchtServerAdapter());
    }
}
