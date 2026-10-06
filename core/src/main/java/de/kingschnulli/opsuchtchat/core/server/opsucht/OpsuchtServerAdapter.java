package de.kingschnulli.opsuchtchat.core.server.opsucht;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.OpsuchtClassifier;
import de.kingschnulli.opsuchtchat.core.OpsuchtHost;
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;

public final class OpsuchtServerAdapter implements ChatServerAdapter {
    private final OpsuchtClassifier classifier = new OpsuchtClassifier();

    @Override
    public String id() {
        return "opsucht";
    }

    @Override
    public String displayName() {
        return "OPSUCHT";
    }

    @Override
    public boolean matchesAddress(String address) {
        return OpsuchtHost.matches(address);
    }

    @Override
    public Classification classify(ChatEnvelope message) {
        return classifier.classify(message);
    }

    @Override
    public Classification classifyStateless(ChatEnvelope message) {
        return classifier.classifyStateless(message);
    }

    @Override
    public void resetSessionState() {
        classifier.resetSessionState();
    }

    @Override
    public String privateMessageCommand(String partner, String message) {
        return "msg " + partner + " " + message;
    }

    @Override
    public String paymentCommand(String partner, String amount) {
        return "pay " + partner + " " + amount;
    }
}
