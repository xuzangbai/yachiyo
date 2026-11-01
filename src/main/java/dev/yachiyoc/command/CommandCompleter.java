package dev.yachiyoc.command;

import dev.yachiyoc.YachiyoC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chat-side completion for the -- command prefix. Command names are discovered
 * from the live registry, so newly registered commands appear automatically.
 */
public final class CommandCompleter {
    private static String query = "";
    private static List<Replacement> current = List.of();
    private static int selected = -1;

    private CommandCompleter() {
    }

    public static List<String> labels(String text) {
        ensure(text);
        return current.stream().map(Replacement::label).toList();
    }

    public static int selectedIndex(String text) {
        ensure(text);
        return selected;
    }

    /**
     * Tab applies the current candidate; repeated Tab presses cycle the list.
     */
    public static String tab(String text) {
        ensure(text);
        if (current.isEmpty()) {
            return null;
        }
        if (isApplied(text)) {
            selected = (selected + 1) % current.size();
        }
        return current.get(selected).text();
    }

    /**
     * Numpad up/down selection. The returned text is immediately put into the
     * chat field, and the popup keeps the selected row highlighted.
     */
    public static String move(String text, int delta) {
        ensure(text);
        if (current.isEmpty()) {
            return null;
        }
        selected = Math.floorMod(selected + delta, current.size());
        return current.get(selected).text();
    }

    public static void reset() {
        query = "";
        current = List.of();
        selected = -1;
    }

    private static void ensure(String text) {
        if (text == null) {
            reset();
            return;
        }
        if (text.equals(query) || isApplied(text)) {
            return;
        }
        query = text;
        current = options(text);
        selected = current.isEmpty() ? -1 : 0;
    }

    private static boolean isApplied(String text) {
        return current.stream().anyMatch(option -> option.text().equals(text));
    }

    private static List<Replacement> options(String input) {
        String prefix = YachiyoC.prefix();
        if (input == null || !input.startsWith(prefix)) {
            return List.of();
        }

        String body = input.substring(prefix.length());
        boolean trailingSpace = body.endsWith(" ");
        String trimmed = body.trim();
        List<String> names = new ArrayList<>(YachiyoC.commands().commandNames());

        if (trimmed.isEmpty()) {
            return names.stream()
                    .map(name -> new Replacement(prefix + name + " ", name))
                    .toList();
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1 && !trailingSpace) {
            String query = parts[0].toLowerCase(Locale.ROOT);
            List<Replacement> result = new ArrayList<>();
            for (ClientCommand command : YachiyoC.commands().commands()) {
                if (!command.name().toLowerCase(Locale.ROOT).startsWith(query)) {
                    continue;
                }
                result.add(new Replacement(prefix + command.name() + " ", command.name()));
                for (String alias : command.aliases()) {
                    if (alias.toLowerCase(Locale.ROOT).startsWith(query)) {
                        result.add(new Replacement(prefix + alias + " ", alias));
                    }
                }
            }
            return result;
        }

        ClientCommand command = YachiyoC.commands().find(parts[0]);
        if (command == null) {
            return List.of();
        }

        int argIndex = trailingSpace ? parts.length - 1 : parts.length - 2;
        String current = trailingSpace ? "" : parts[parts.length - 1];
        List<String> candidates = YachiyoC.commands().suggestionsFor(command.name(), argIndex);
        List<Replacement> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (!candidate.toLowerCase(Locale.ROOT).startsWith(current.toLowerCase(Locale.ROOT))) {
                continue;
            }
            StringBuilder before = new StringBuilder(prefix).append(command.name()).append(' ');
            int beforeEnd = trailingSpace ? parts.length : parts.length - 1;
            for (int i = 1; i < beforeEnd; i++) {
                before.append(parts[i]).append(' ');
            }
            result.add(new Replacement(before + candidate + " ", candidate));
        }
        return result;
    }

    private record Replacement(String text, String label) {
    }
}
