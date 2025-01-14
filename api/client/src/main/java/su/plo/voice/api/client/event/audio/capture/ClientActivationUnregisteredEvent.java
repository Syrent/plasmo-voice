package su.plo.voice.api.client.event.audio.capture;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.capture.ClientActivationManager;
import su.plo.voice.api.event.Event;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This event is fired once {@link ClientActivation} is unregistered in {@link ClientActivationManager}
 */
public final class ClientActivationUnregisteredEvent implements Event {

    @Getter
    private final ClientActivation activation;

    public ClientActivationUnregisteredEvent(@NotNull ClientActivation activation) {
        this.activation = checkNotNull(activation, "activation cannot be null");
    }
}
