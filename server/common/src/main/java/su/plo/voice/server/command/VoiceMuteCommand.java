package su.plo.voice.server.command;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.lib.chat.TextComponent;
import su.plo.lib.server.MinecraftServerLib;
import su.plo.lib.server.command.MinecraftCommand;
import su.plo.lib.server.command.MinecraftCommandSource;
import su.plo.lib.server.entity.MinecraftServerPlayer;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.mute.MuteDurationUnit;
import su.plo.voice.server.mute.VoiceMuteManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class VoiceMuteCommand implements MinecraftCommand {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^([0-9]*)([mhdwsu]|permanent)?$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^([0-9]*)$");

    private final PlasmoVoiceServer voiceServer;
    private final MinecraftServerLib minecraftServer;

    @Override
    public void execute(@NotNull MinecraftCommandSource source, @NotNull String[] arguments) {
        if (arguments.length == 0) {
            source.sendMessage(TextComponent.translatable("commands.plasmovoice.mute.usage"));
            return;
        }

        Optional<MinecraftServerPlayer> player = minecraftServer.getPlayerByName(arguments[0]);
        if (!player.isPresent()) {
            source.sendMessage(TextComponent.translatable("commands.plasmovoice.player_not_found"));
            return;
        }

        VoiceMuteManager muteManager = (VoiceMuteManager) voiceServer.getMuteManager();

        if (muteManager.getMute(player.get().getUUID()).isPresent()) {
            source.sendMessage(TextComponent.translatable(
                    "commands.plasmovoice.mute.already_muted",
                    player.get().getName()
            ));
            return;
        }

        int reasonSpaceIndex = 1;
        MuteDurationUnit durationUnit = null;
        long duration = 0;

        if (arguments.length > 1) {
            String durationArg = arguments[1];
            Matcher matcher = DURATION_PATTERN.matcher(durationArg);
            if (matcher.find()) {
                String type = matcher.group(2);
                if (type == null || !type.equals("permanent")) {
                    durationUnit = parseDurationUnit(type);
                }

                duration = parseDuration(matcher.group(1), durationUnit);
                reasonSpaceIndex = 2;
            }

        }

        String reason = null;
        if (arguments.length > reasonSpaceIndex) {
            reason = String.join(" ", Arrays.copyOfRange(arguments, reasonSpaceIndex, arguments.length));
        }

        if (durationUnit == null) {
            source.sendMessage(TextComponent.translatable(
                    "commands.plasmovoice.mute.permanently_muted",
                    player.get().getName(),
                    muteManager.formatMuteReason(reason)
            ));
        } else {
            try {
                source.sendMessage(TextComponent.translatable(
                        "commands.plasmovoice.mute.temporally_muted",
                        player.get().getName(),
                        muteManager.formatDurationUnit(duration, durationUnit),
                        muteManager.formatMuteReason(reason)
                ));
            } catch (IllegalArgumentException e) {
                source.sendMessage(TextComponent.literal(e.getMessage()));
            }
        }

        UUID mutedBy = null;
        if (source instanceof MinecraftServerPlayer) {
            mutedBy = ((MinecraftServerPlayer) source).getUUID();
        }

        muteManager.mute(
                player.get().getUUID(),
                mutedBy,
                duration,
                durationUnit,
                reason,
                false
        );
    }

    @Override
    public boolean hasPermission(@NotNull MinecraftCommandSource source, @Nullable String[] arguments) {
        return source.hasPermission("voice.mute");
    }

    @Override
    public List<String> suggest(@NotNull MinecraftCommandSource source, @NotNull String[] arguments) {
        if (arguments.length <= 1) {
            return Suggestions.players(minecraftServer, source, arguments.length > 0 ? arguments[0] : "");
        } else if (arguments.length == 2) {
            if (arguments[1].isEmpty()) {
                return ImmutableList.of("permanent");
            } else if ("permanent".startsWith(arguments[1])) {
                return ImmutableList.of("permanent");
            }

            Matcher matcher = INTEGER_PATTERN.matcher(arguments[1]);
            if (matcher.find()) {
                List<String> durations = new ArrayList<>();
                durations.add(arguments[1] + "s");
                durations.add(arguments[1] + "m");
                durations.add(arguments[1] + "h");
                durations.add(arguments[1] + "d");
                durations.add(arguments[1] + "w");
                return durations;
            }
        }

        return MinecraftCommand.super.suggest(source, arguments);
    }

    private long parseDuration(@NotNull String durationString, @Nullable MuteDurationUnit durationUnit) {
        if (durationUnit == null) return 0L;

        long duration = Long.parseLong(durationString);
        if (durationUnit == MuteDurationUnit.TIMESTAMP) {
            return duration * 1_000L;
        }

        return duration;
    }

    private MuteDurationUnit parseDurationUnit(@Nullable String type) {
        switch (Strings.nullToEmpty(type)) {
            case "m":
                return MuteDurationUnit.MINUTE;
            case "h":
                return MuteDurationUnit.HOUR;
            case "d":
                return MuteDurationUnit.DAY;
            case "w":
                return MuteDurationUnit.WEEK;
            case "u":
                return MuteDurationUnit.TIMESTAMP;
            default:
                return MuteDurationUnit.SECOND;
        }
    }
}
