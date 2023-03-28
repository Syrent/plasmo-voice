package su.plo.lib.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import lombok.RequiredArgsConstructor;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.lib.api.chat.MinecraftTextStyle;
import su.plo.lib.api.proxy.MinecraftProxyLib;
import su.plo.lib.api.proxy.command.MinecraftProxyCommand;
import su.plo.lib.api.server.command.MinecraftCommandSource;
import su.plo.lib.velocity.chat.ComponentTextConverter;

import java.util.List;

@RequiredArgsConstructor
public final class VelocityCommand implements SimpleCommand {

    private final VelocityCommandManager commandManager;
    private final MinecraftProxyCommand command;

    @Override
    public void execute(Invocation invocation) {
        MinecraftCommandSource source = commandManager.getCommandSource(invocation.source());

        if (!command.hasPermission(source, invocation.arguments())) {
            source.sendMessage(MinecraftTextComponent.translatable("pv.error.no_permissions"));
            return;
        }

        command.execute(source, invocation.arguments());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return command.suggest(commandManager.getCommandSource(invocation.source()), invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return command.hasPermission(commandManager.getCommandSource(invocation.source()), null);
    }
}
