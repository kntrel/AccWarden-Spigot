package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.BucketCapacityFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientFinding;
import com.kntrel.mc.accwarden.gateway.policy.Finding;
import com.kntrel.mc.accwarden.gateway.policy.LoginFinding;
import com.kntrel.mc.runical.bukkit.Translator;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ThrottleMessageFormatter {

    private final Translator messageTranslator_;
    private final Translator timeTranslator_;
    private final Clock clock_;

    ThrottleMessageFormatter(Translator translator) {
        this(translator, Clock.systemUTC());
    }

    ThrottleMessageFormatter(Translator translator, Clock clock) {
        Objects.requireNonNull(translator, "translator");
        this.messageTranslator_ = translator
                .getChild("kicked_message")
                .getChild("throttled");
        this.timeTranslator_ = translator.getChild("time_format");
        this.clock_ = Objects.requireNonNull(clock, "clock");
    }

    String format(Player player, Decision.Throttled throttled) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(throttled, "throttled");

        String remainingTime = this.formatTime_(
                player,
                Duration.between(this.clock_.instant(), throttled.penalty().until())
        );
        Reason reason = Reason.from_(throttled.penalty().cause());
        return this.messageTranslator_
                .translate(player, reason.key_)
                .argument("remaining_time", remainingTime)
                .orDefault(reason.fallback_(remainingTime))
                .message();
    }

    private String formatTime_(Player player, Duration remaining) {
        long totalSeconds = roundedSeconds_(remaining);
        long hours = totalSeconds / 3_600;
        long minutes = totalSeconds % 3_600 / 60;
        long seconds = totalSeconds % 60;
        List<String> parts = new ArrayList<>(3);

        if (hours > 0) {
            parts.add(this.formatUnit_(player, "hour", hours));
        }
        if (minutes > 0) {
            parts.add(this.formatUnit_(player, "minute", minutes));
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(this.formatUnit_(player, "second", seconds));
        }

        return switch (parts.size()) {
            case 1 -> parts.getFirst();
            case 2 -> this.timeTranslator_
                    .translate(player, "join.two")
                    .argument("first", parts.get(0))
                    .argument("second", parts.get(1))
                    .orDefault(parts.get(0) + " and " + parts.get(1))
                    .message();
            case 3 -> this.timeTranslator_
                    .translate(player, "join.three")
                    .argument("first", parts.get(0))
                    .argument("second", parts.get(1))
                    .argument("third", parts.get(2))
                    .orDefault(parts.get(0) + ", " + parts.get(1) + " and " + parts.get(2))
                    .message();
            default -> throw new IllegalStateException("A duration must contain between one and three parts.");
        };
    }

    private String formatUnit_(Player player, String unit, long value) {
        String suffix = value == 1 ? "one" : "other";
        String fallback = value + " " + unit + (value == 1 ? "" : "s");
        return this.timeTranslator_
                .translate(player, "unit." + unit + "." + suffix)
                .argument("value", value)
                .orDefault(fallback)
                .message();
    }

    private static long roundedSeconds_(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0 && seconds < Long.MAX_VALUE) {
            seconds++;
        }
        return seconds;
    }

    private enum Reason {
        CONNECTION_ATTEMPTS(
                "connection_attempts",
                "You have made too many connection attempts. Please wait %s before trying again."
        ),
        LOGIN_ATTEMPTS(
                "login_attempts",
                "You have made too many login attempts. Please wait %s before trying again."
        ),
        GLOBAL_LIMIT(
                "global_limit",
                "The server is experiencing heavy traffic. Please wait %s before trying again."
        );

        private final String key_;
        private final String fallback_;

        Reason(String key, String fallback) {
            this.key_ = key;
            this.fallback_ = fallback;
        }

        private String fallback_(String remainingTime) {
            return this.fallback_.formatted(remainingTime);
        }

        private static Reason from_(Finding cause) {
            if (cause instanceof ClientFinding) {
                return CONNECTION_ATTEMPTS;
            }
            if (cause instanceof LoginFinding) {
                return LOGIN_ATTEMPTS;
            }
            if (cause instanceof BucketCapacityFinding) {
                return GLOBAL_LIMIT;
            }
            throw new IllegalArgumentException(
                    "Unsupported throttle cause: " + cause.getClass().getName()
            );
        }
    }
}
