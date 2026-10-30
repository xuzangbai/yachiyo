package dev.yachiyoc.command;

import java.util.List;

public interface ClientCommand {
    String name();

    default List<String> aliases() {
        return List.of();
    }

    default String usage() {
        return name();
    }

    default String description() {
        return "";
    }

    void execute(CommandContext context);
}
