package dev.yachiyoc.command;

import java.util.List;
import java.util.function.Consumer;

public record SimpleCommand(
        String name,
        List<String> aliases,
        String usage,
        String description,
        Consumer<CommandContext> action
) implements ClientCommand {
    @Override
    public void execute(CommandContext context) {
        action.accept(context);
    }
}
