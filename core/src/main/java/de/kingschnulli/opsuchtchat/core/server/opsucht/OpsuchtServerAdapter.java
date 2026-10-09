package de.kingschnulli.opsuchtchat.core.server.opsucht;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.OpsuchtClassifier;
import de.kingschnulli.opsuchtchat.core.OpsuchtHost;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.SocialEvent;
import de.kingschnulli.opsuchtchat.core.presentation.SocialEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.SocialEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.SocialFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.TextRange;
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionMode;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandMode;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerHubPage;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpsuchtServerAdapter implements ChatServerAdapter {
    private static final Pattern PUBLIC_CHAT = Pattern.compile(
            "(?s)^\\s*»?\\s*(.*?)\\s*\\|\\s*(.+?)\\s*»\\s*(.*?)\\s*»?\\s*$"
    );
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(-?\\d[\\d.,]*)\\s*(\\$|dollar|k|kk|m|mio\\.?|b)?"
    );
    private static final Pattern ITEM = Pattern.compile("\\[([^\\]]+)]");
    private static final Pattern TPA_ACTOR = Pattern.compile(
            "(?i)(?:spieler\\s+)?([A-Za-z0-9_.~-]{1,32}).{0,50}(?:teleport|tpa).{0,35}(?:anfrage|anfrag|geschickt|möchte|moechte)"
    );
    private static final Pattern TPA_ACTOR_REVERSED = Pattern.compile(
            "(?i)(?:teleport|tpa).{0,35}(?:anfrage|anfrag).{0,50}(?:von\\s+)?([A-Za-z0-9_.~-]{1,32})"
    );

    private static final List<ServerHubPage> HUB_PAGES = List.of(
            new ServerHubPage("quick", "Schnell", List.of(
                    run("home", "/home", "Homes öffnen", "home", List.of()),
                    run("spawn", "/spawn", "Zum Spawn", "spawn", List.of()),
                    prefill("pay", "/pay", "Spieler bezahlen", "pay", List.of()),
                    run("ah", "/ah", "Auktionshaus", "auktionshaus", List.of("ah")),
                    run("ec", "/ec", "Enderchest", "enderchest", List.of("ec")),
                    run("freund", "/freund", "Freunde verwalten", "freund", List.of("freunde")),
                    run("cb", "/cb", "CityBuild", "citybuild", List.of("cb")),
                    run("jobs", "/jobs", "Jobs", "jobs", List.of("job")),
                    run("farm", "/farm", "Farmwelten", "farm", List.of("farmserver")),
                    run("plot", "/plot", "Plot-Menü", "plot", List.of("p")),
                    run("bank", "/bank", "Bank", "bank", List.of()),
                    run("wb", "/wb", "Werkbank", "werkbank", List.of("wb"))
            )),
            new ServerHubPage("social", "Sozial", List.of(
                    run("freund", "/freund", "Freunde verwalten", "freund", List.of("freunde")),
                    prefill("msg", "/msg", "Private Nachricht", "msg", List.of()),
                    prefill("reply", "/r", "Letztem Kontakt antworten", "reply", List.of("r")),
                    prefill("ignore", "/ignore", "Spieler ignorieren", "ignore", List.of()),
                    run("msgtoggle", "/msgtoggle", "PN-Empfang einstellen", "msgtoggle", List.of()),
                    prefill("tpa", "/tpa", "Teleport-Anfrage senden", "tpa", List.of()),
                    prefill("tpahere", "/tpahere", "Spieler zu dir anfragen", "tpahere", List.of()),
                    run("tpaccept", "/tpaccept", "TPA annehmen", "tpaccept", List.of("tpy", "tpyes", "tpaaccept")),
                    run("tpadeny", "/tpadeny", "TPA ablehnen", "tpadeny", List.of("tpn", "tpno", "tpadecline")),
                    run("tpatoggle", "/tpatoggle", "TPA-Empfang einstellen", "tpatoggle", List.of("tptoggle")),
                    run("clan", "/clan", "Clan-Menü", "clan", List.of("c")),
                    prefill("gift", "/gift", "Rang verschenken", "gift", List.of()),
                    prefill("realname", "/realname", "Echten Namen prüfen", "realname", List.of())
            )),
            new ServerHubPage("travel", "Reisen", List.of(
                    run("spawn", "/spawn", "Zum Spawn", "spawn", List.of()),
                    run("citybuild", "/cb", "Zum CityBuild", "citybuild", List.of("cb")),
                    run("lobby", "/lobby", "Zur Lobby", "lobby", List.of("l", "hub")),
                    run("farm", "/farm", "Farmwelten", "farm", List.of("farmserver")),
                    run("pvp", "/pvp", "PVP-Server", "pvp", List.of()),
                    run("redstone", "/redstone", "Redstone-Welt", "redstone", List.of()),
                    run("navigator", "/nav", "Server-Navigator", "navigator", List.of("nav", "server")),
                    run("rtp", "/rtp", "Zufälliger Farmwelt-TP", "randomteleport", List.of("rtp")),
                    run("sw", "/sw", "S-Warp Übersicht", "sw", List.of()),
                    run("swarp", "/swarp", "S-Warp Hilfe", "swarp", List.of())
            )),
            new ServerHubPage("economy", "Wirtschaft", List.of(
                    run("money", "/money", "Kontostand", "money", List.of()),
                    prefill("pay", "/pay", "Spieler bezahlen", "pay", List.of()),
                    run("ah", "/ah", "Auktionshaus", "auktionshaus", List.of("ah")),
                    run("bank", "/bank", "Bank", "bank", List.of()),
                    run("jobs", "/jobs", "Jobs", "jobs", List.of("job")),
                    run("immo", "/immo", "Immobilienmarkt", "immo", List.of("im")),
                    prefill("werbung", "/werbung", "Werbung senden", "werbung", List.of()),
                    run("shopcreate", "/shopcreate", "Shopkiste erstellen", "shopcreate", List.of()),
                    run("shopinfo", "/shopinfo", "Shopkiste prüfen", "shopinfo", List.of("sinfo")),
                    run("shopupdate", "/shopupdate", "Shop/Rang aktualisieren", "shopupdate", List.of()),
                    run("code", "/code", "Affiliate-System", "code", List.of()),
                    run("rang", "/rang", "Rang-Shop", "rang", List.of())
            )),
            new ServerHubPage("home", "Zuhause", List.of(
                    run("home", "/home", "Homes öffnen", "home", List.of()),
                    prefill("sethome", "/sethome", "Home setzen", "sethome", List.of("home set")),
                    prefill("delhome", "/delhome", "Home löschen", "delhome", List.of("home delete")),
                    run("plot", "/plot", "Plot-Menü", "plot", List.of("p")),
                    run("tresor", "/tresor", "Tresor", "tresor", List.of("safe")),
                    run("umzug", "/umzug", "Umzug-Menü", "umzug", List.of())
            )),
            new ServerHubPage("utility", "Utility", List.of(
                    run("anvil", "/anvil", "Amboss", "anvil", List.of()),
                    prefill("armor", "/armor", "Rüstung ansehen", "armor", List.of("invsee armor")),
                    run("booster", "/booster", "Booster-Menü", "booster", List.of()),
                    run("cstoggle", "/cstoggle", "Shopkisten-Nachrichten", "cstoggle", List.of()),
                    run("disguise", "/disguise", "Verwandlung", "disguise", List.of("dis")),
                    run("emoji", "/emoji", "Emoji-Menü", "emoji", List.of("smiley")),
                    run("ec", "/ec", "Enderchest", "enderchest", List.of("ec")),
                    run("farben", "/farben", "Chatfarben", "farben", List.of("colorcodes", "chatcolor")),
                    run("feed", "/feed", "Hunger auffüllen", "feed", List.of()),
                    run("fly", "/fly", "Flugmodus", "fly", List.of()),
                    run("hat", "/hat", "Item als Hut", "hat", List.of()),
                    run("haustiere", "/pet", "Haustiere", "haustiere", List.of("pet")),
                    run("heal", "/heal", "Leben auffüllen", "heal", List.of()),
                    prefill("invsee", "/invsee", "Inventar ansehen", "inventorysee", List.of("invsee")),
                    run("iteminfo", "/iteminfo", "Item-Informationen", "iteminfo", List.of("iinfo")),
                    run("kit", "/kit", "Kits", "kit", List.of("kits")),
                    run("kopieren", "/mapcopy", "Karte kopieren", "kopieren", List.of("kartekopieren", "mapcopy")),
                    run("kompressor", "/kompressor", "Kompressor", "kompressor", List.of()),
                    run("minion", "/minion", "Minion-Menü", "minion", List.of()),
                    prefill("nick", "/nick", "Nickname setzen", "nick", List.of()),
                    run("perks", "/perks", "Perks", "perks", List.of("perk")),
                    run("prefix", "/prefix", "Prefix-Menü", "prefix", List.of()),
                    prefill("rainbow", "/rainbow", "Regenbogen-Nachricht", "rainbow", List.of("rb")),
                    prefill("rename", "/rename", "Item umbenennen", "rename", List.of()),
                    prefill("sign", "/sign", "Item signieren", "sign", List.of()),
                    prefill("skull", "/skull", "Spielerkopf holen", "skull", List.of("head")),
                    run("smiley", "/smiley", "Emoji-Menü", "smiley", List.of("emoji")),
                    run("trash", "/trash", "Mülleimer", "trash", List.of("disposal")),
                    run("undis", "/undis", "Verwandlung beenden", "undis", List.of()),
                    run("unnick", "/unnick", "Nickname entfernen", "unnick", List.of()),
                    run("wb", "/wb", "Werkbank", "werkbank", List.of("wb"))
            )),
            new ServerHubPage("info", "Info", List.of(
                    run("2fa", "/2fa", "2FA verwalten", "2fa", List.of()),
                    run("belohnung", "/belohnung", "Belohnungen", "belohnung", List.of("belohnungen", "reward")),
                    run("discord", "/discord", "Discord-Link", "discord", List.of("dc")),
                    run("dlink", "/dlink", "Discord verknüpfen", "dlink", List.of("link")),
                    run("dunlink", "/dunlink", "Discord-Verknüpfung lösen", "dunlink", List.of("unlink")),
                    run("erfolge", "/erfolge", "Achievements", "erfolge", List.of("a", "achievement", "achievements")),
                    run("geburtstag", "/geburtstag", "Geburtstags-Menü", "geburtstag", List.of()),
                    run("instagram", "/instagram", "Instagram-Link", "instagram", List.of("insta")),
                    run("online", "/online", "Spielzeit", "online", List.of("onlinetime", "spielzeit")),
                    run("oppass", "/oppass", "OP Pass", "oppass", List.of("op")),
                    run("regeln", "/regeln", "Regeln", "regeln", List.of("regel")),
                    run("shop", "/shop", "Shop-Link", "shop", List.of("store")),
                    run("skiptutorial", "/skiptutorial", "Tutorial überspringen", "skiptutorial", List.of()),
                    run("teamspeak", "/teamspeak", "TeamSpeak", "teamspeak", List.of("ts3", "ts")),
                    run("tutorial", "/tutorial", "Tutorial/Hilfe", "tutorial", List.of("hilfe")),
                    run("twitter", "/twitter", "Twitter-Link", "twitter", List.of()),
                    run("twitch", "/twitch", "Twitch-Link", "twitch", List.of()),
                    run("unverify", "/unverify", "TeamSpeak-Verknüpfung lösen", "unverify", List.of()),
                    run("verify", "/verify", "TeamSpeak verknüpfen", "verify", List.of()),
                    run("vote", "/vote", "Vote-Seite", "vote", List.of()),
                    run("wiki", "/wiki", "OPSUCHT Wiki", "wiki", List.of())
            ))
    );

    private static final List<String> DEFAULT_FAVORITES = List.of("home", "spawn", "pay", "ah");

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

    @Override
    public String friendAddCommand(String partner) {
        return "freund hinzufügen " + partner;
    }

    @Override
    public String friendMenuCommand() {
        return "freund";
    }

    @Override
    public String friendRequestsCommand() {
        return "freund anfragen";
    }

    @Override
    public List<PlayerActionSpec> playerActions(String partner) {
        if (partner == null || partner.isBlank()) {
            return List.of();
        }

        return List.of(
                new PlayerActionSpec(
                        "friend",
                        "+Freund",
                        friendAddCommand(partner),
                        PlayerActionMode.RUN,
                        true
                ),
                new PlayerActionSpec(
                        "pay",
                        "Pay",
                        paymentCommand(partner, ""),
                        PlayerActionMode.PAY_AMOUNT,
                        true
                ),
                new PlayerActionSpec(
                        "tpa",
                        "TPA",
                        "tpa " + partner,
                        PlayerActionMode.RUN,
                        false
                ),
                new PlayerActionSpec(
                        "ignore",
                        "Ignorieren",
                        "ignore " + partner,
                        PlayerActionMode.RUN,
                        false
                ),
                new PlayerActionSpec(
                        "realname",
                        "Realname",
                        "realname " + partner,
                        PlayerActionMode.RUN,
                        false
                )
        );
    }

    @Override
    public List<ServerHubPage> serverHubPages() {
        return HUB_PAGES;
    }

    @Override
    public List<String> defaultServerCommandFavorites() {
        return DEFAULT_FAVORITES;
    }

    @Override
    public PublicChatLine parsePublicChat(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        String rank = clean(matcher.group(1));
        String player = clean(matcher.group(2));
        String body = clean(matcher.group(3));

        if (!player.matches("[A-Za-z0-9_.~-]{1,32}")) {
            return null;
        }

        return new PublicChatLine(rank, player, body);
    }

    @Override
    public TextRange publicPlayerRange(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        int start = matcher.start(2);
        int end = matcher.end(2);
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new TextRange(start, end);
    }

    @Override
    public TextRange publicBodyRange(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        int start = matcher.start(3);
        int end = matcher.end(3);
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new TextRange(start, end);
    }

    @Override
    public TextRange serverBodyRange(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String cleaned = clean(text);
        String lower = cleaned.toLowerCase(Locale.ROOT);
        int start = 0;

        if (lower.startsWith("opsucht")) {
            int separator = text.indexOf('»');
            if (separator >= 0) {
                start = separator + 1;
            }
        } else if (lower.startsWith("freunde") && lower.contains("opsucht")) {
            int bracket = text.lastIndexOf(']');
            if (bracket >= 0) {
                start = bracket + 1;
            }
        }

        start = skipLegacyFormattingAndWhitespace(text, start);
        int end = trimLegacyFormattingAndWhitespaceEnd(text, text.length());
        return new TextRange(Math.min(start, end), end);
    }

    @Override
    public String serverClickActionLabel(String messageText, String clickableText) {
        String message = clean(messageText).toLowerCase(Locale.ROOT);
        String clickable = clean(clickableText);
        String clickableLower = clickable.toLowerCase(Locale.ROOT);

        if (clickableLower.contains("ablehn")) {
            return "Ablehnen";
        }
        if (clickableLower.contains("annehm")) {
            return "Annehmen";
        }
        if (clickable.isBlank() || clickable.equalsIgnoreCase("hier")) {
            boolean accept = message.contains("annehmen");
            boolean deny = message.contains("ablehnen");
            if (accept && !deny) {
                return "Annehmen";
            }
            if (deny && !accept) {
                return "Ablehnen";
            }
            return "Aktion";
        }
        return clickable;
    }

    @Override
    public ServerFeedEvent parseServerEvent(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return null;
        }

        String body = cleaned;
        String lower = cleaned.toLowerCase(Locale.ROOT);

        if (lower.startsWith("opsucht")) {
            int separator = cleaned.indexOf('»');
            if (separator >= 0) {
                body = cleaned.substring(separator + 1).trim();
            }
        } else if (lower.startsWith("freunde") && lower.contains("opsucht")) {
            int bracket = cleaned.indexOf(']');
            if (bracket >= 0 && bracket + 1 < cleaned.length()) {
                body = cleaned.substring(bracket + 1).trim();
            }
        }

        String bodyLower = body.toLowerCase(Locale.ROOT);

        if (bodyLower.contains("bungeecord") || bodyLower.contains("does not provide recipes to jei")) {
            return new ServerFeedEvent(ServerEventKind.INFO, "Client / Proxy", body, null);
        }
        if (body.startsWith("➜")) {
            String action = body.substring(1).trim();
            return new ServerFeedEvent(ServerEventKind.ACTION, "Befehl", action, action);
        }
        if (bodyLower.contains(" hat dir ") && bodyLower.contains("$") && bodyLower.contains("gegeben")) {
            return new ServerFeedEvent(ServerEventKind.MONEY, "Zahlung erhalten", body, null);
        }
        if (bodyLower.contains("teleport")) {
            return new ServerFeedEvent(ServerEventKind.TELEPORT, "Teleport", body, extractCommand(body));
        }
        if (bodyLower.contains("/vote") || bodyLower.contains("vote unterstützt")) {
            return new ServerFeedEvent(ServerEventKind.VOTE, "Vote", body, null);
        }
        if (bodyLower.contains("gefangen")
                || bodyLower.contains("fänge verkauft")
                || bodyLower.contains("hat angebissen")
                || bodyLower.contains("fadenkreuz")) {
            return new ServerFeedEvent(ServerEventKind.FISHING, "Angeln", body, null);
        }
        if (bodyLower.contains("verkaufsangebot")
                || bodyLower.contains("preis für")
                || bodyLower.contains("marktsystem")
                || bodyLower.contains("/markt")) {
            return new ServerFeedEvent(ServerEventKind.MARKET, "Markt", body, extractCommand(body));
        }
        if (bodyLower.contains("job-level") || bodyLower.contains("lohn von")) {
            return new ServerFeedEvent(ServerEventKind.JOB, "Job", body, null);
        }
        if (bodyLower.contains("booster")) {
            return new ServerFeedEvent(ServerEventKind.BOOSTER, "Booster", body, null);
        }

        return new ServerFeedEvent(ServerEventKind.INFO, "OPSUCHT", body, extractCommand(body));
    }

    @Override
    public SocialFeedEvent parseSocialEvent(String text) {
        String body = clean(text);
        if (body.isBlank()) {
            return null;
        }

        String initialLower = body.toLowerCase(Locale.ROOT);
        if (initialLower.startsWith("opsucht")) {
            int separator = body.indexOf('»');
            if (separator >= 0) {
                body = body.substring(separator + 1).trim();
            }
        } else if (initialLower.startsWith("freunde") && initialLower.contains("opsucht")) {
            int bracket = body.lastIndexOf(']');
            if (bracket >= 0 && bracket + 1 < body.length()) {
                body = body.substring(bracket + 1).trim();
            }
        }

        String lower = body.toLowerCase(Locale.ROOT);
        boolean teleportRequest = (lower.contains("teleport") || lower.contains("tpa"))
                && (lower.contains("anfrage")
                    || lower.contains("anfrag")
                    || lower.contains("annehmen")
                    || lower.contains("tpaccept"));

        if (!teleportRequest) {
            return null;
        }

        String actor = extractTeleportActor(body);

        return new SocialFeedEvent(
                SocialEventKind.TELEPORT_REQUEST,
                actor,
                actor == null ? "Teleport-Anfrage" : "Teleport-Anfrage · " + actor,
                body
        );
    }

    @Override
    public SocialFeedEvent parseSocialEvent(String text, List<String> interactionCommands) {
        SocialFeedEvent fromText = parseSocialEvent(text);
        if (fromText != null) {
            return fromText;
        }

        if (interactionCommands == null) {
            return null;
        }

        boolean teleportAction = interactionCommands.stream()
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("tpaccept")
                        || value.contains("tpyes")
                        || value.contains("tpadeny")
                        || value.contains("tpdeny")
                        || value.contains("tpno"));

        if (!teleportAction) {
            return null;
        }

        String body = clean(text);
        return new SocialFeedEvent(
                SocialEventKind.TELEPORT_REQUEST,
                extractTeleportActor(body),
                "Teleport-Anfrage",
                body
        );
    }

    @Override
    public SocialEvent parseSocialEvent(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return null;
        }

        String body = cleaned;
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.startsWith("opsucht")) {
            int separator = cleaned.indexOf('»');
            if (separator >= 0) {
                body = cleaned.substring(separator + 1).trim();
            }
        }

        String bodyLower = body.toLowerCase(Locale.ROOT);

        if ((bodyLower.contains("teleport-anfrage") || bodyLower.contains("teleportanfrage"))
                && (bodyLower.contains("geschickt") || bodyLower.contains("gesendet"))) {
            String actor = extractLeadingActor(body);
            return new SocialEvent(
                    SocialEventKind.TELEPORT_REQUEST,
                    actor,
                    "Teleport-Anfrage",
                    body
            );
        }

        // Friend requests intentionally stay unparsed until we have real OPSUCHT
        // examples in debug data. Do not guess server phrasing.
        return null;
    }

    @Override
    public AuctionFeedEvent parseAuctionEvent(String text) {
        PublicChatLine chat = parsePublicChat(text);
        if (chat == null) {
            return null;
        }

        String body = chat.body();
        String lower = body.toLowerCase(Locale.ROOT);
        AuctionEventKind kind;

        if (lower.contains("verkauft")) {
            kind = AuctionEventKind.SOLD;
        } else if (lower.matches(".*\\bzum\\s+(?:ersten|zweiten|dritten|2\\.|3\\.).*")) {
            kind = AuctionEventKind.COUNTDOWN;
        } else if (lower.contains("versteiger")) {
            kind = AuctionEventKind.START;
        } else if (lower.matches("\\s*-?\\d[\\d.,]*\\s*(?:\\$|dollar|k|kk|m|mio\\.?|b)?\\s*")
                || lower.matches(".*\\bbiete\\s+-?\\d.*")
                || lower.matches(".*\\bgebot\\b.*")) {
            kind = AuctionEventKind.BID;
        } else {
            kind = AuctionEventKind.OTHER;
        }

        Matcher amountMatcher = AMOUNT.matcher(body);
        String amount = amountMatcher.find()
                ? amountMatcher.group(1) + (amountMatcher.group(2) == null ? "" : amountMatcher.group(2))
                : null;

        Matcher itemMatcher = ITEM.matcher(body);
        String item = itemMatcher.find() ? itemMatcher.group(1) : null;

        return new AuctionFeedEvent(kind, chat.player(), body, amount, item);
    }

    @Override
    public String identityBase(String value) {
        if (value == null) {
            return "";
        }

        String normalized = clean(value).toLowerCase(Locale.ROOT);
        while (normalized.startsWith("~") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static ServerCommandSpec run(
            String id,
            String label,
            String description,
            String command,
            List<String> aliases
    ) {
        return new ServerCommandSpec(id, label, command, description, aliases, ServerCommandMode.RUN);
    }

    private static ServerCommandSpec prefill(
            String id,
            String label,
            String description,
            String command,
            List<String> aliases
    ) {
        return new ServerCommandSpec(id, label, command, description, aliases, ServerCommandMode.PREFILL);
    }

    private static String extractTeleportActor(String body) {
        Matcher actorMatcher = TPA_ACTOR.matcher(body);
        if (actorMatcher.find()) {
            return actorMatcher.group(1);
        }

        Matcher reversed = TPA_ACTOR_REVERSED.matcher(body);
        return reversed.find() ? reversed.group(1) : null;
    }

    private static String extractLeadingActor(String body) {
        Matcher matcher = Pattern.compile(
                "^([A-Za-z0-9_.~-]{1,32})\\s+hat\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int skipLegacyFormattingAndWhitespace(String text, int start) {
        int index = Math.max(0, start);
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '§' && index + 1 < text.length()) {
                index += 2;
                continue;
            }
            break;
        }
        return index;
    }

    private static int trimLegacyFormattingAndWhitespaceEnd(String text, int end) {
        int index = Math.min(text.length(), end);
        while (index > 0) {
            char current = text.charAt(index - 1);
            if (Character.isWhitespace(current)) {
                index--;
                continue;
            }
            if (index >= 2 && text.charAt(index - 2) == '§') {
                index -= 2;
                continue;
            }
            break;
        }
        return index;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String extractCommand(String body) {
        Matcher matcher = Pattern.compile("(?<!\\S)/(?:[a-zA-Z][a-zA-Z0-9_-]*)(?:\\s+[^\\s]+)*").matcher(body);
        return matcher.find() ? matcher.group() : null;
    }
}
