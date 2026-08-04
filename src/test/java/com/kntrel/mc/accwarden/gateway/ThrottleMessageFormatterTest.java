package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.BucketCapacityFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientFinding;
import com.kntrel.mc.accwarden.gateway.policy.Finding;
import com.kntrel.mc.accwarden.gateway.policy.LoginFinding;
import com.kntrel.mc.runical.bukkit.Translator;
import com.kntrel.mc.runical.bukkit.dsl.PlayerTranslationJob;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleMessageFormatterTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final Map<String, String> TRANSLATIONS = Map.ofEntries(
            Map.entry(
                    "kicked_message.throttled.connection_attempts",
                    "connections: {remaining_time}"
            ),
            Map.entry(
                    "kicked_message.throttled.login_attempts",
                    "logins: {remaining_time}"
            ),
            Map.entry(
                    "kicked_message.throttled.global_limit",
                    "traffic: {remaining_time}"
            ),
            Map.entry("time_format.unit.hour.one", "{value} hour"),
            Map.entry("time_format.unit.hour.other", "{value} hours"),
            Map.entry("time_format.unit.minute.one", "{value} minute"),
            Map.entry("time_format.unit.minute.other", "{value} minutes"),
            Map.entry("time_format.unit.second.one", "{value} second"),
            Map.entry("time_format.unit.second.other", "{value} seconds"),
            Map.entry("time_format.join.two", "{first} and {second}"),
            Map.entry("time_format.join.three", "{first}, {second} and {third}")
    );

    private final Player player_ = proxy_(Player.class);
    private final ThrottleMessageFormatter formatter_ = new ThrottleMessageFormatter(
            translator_(TRANSLATIONS),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void formatsEachThrottleCauseWithItsRemainingTime() {
        assertEquals(
                "connections: 1 hour, 1 minute and 1 second",
                this.format_(new ClientFinding(
                        5,
                        5,
                        NOW.plusSeconds(3_661),
                        ClientFinding.Threshold.CLIENT_CONNECTIONS
                ))
        );
        assertEquals(
                "logins: 2 hours, 2 minutes and 2 seconds",
                this.format_(new LoginFinding(
                        5,
                        5,
                        NOW.plusSeconds(7_322),
                        LoginFinding.Threshold.CLIENT_FAILED_LOGINS
                ))
        );
        assertEquals(
                "traffic: 59 seconds",
                this.format_(new BucketCapacityFinding(1_000, 1_000, NOW.plusSeconds(59)))
        );
    }

    @Test
    void roundsPartialRemainingSecondsUp() {
        assertEquals(
                "logins: 1 second",
                this.format_(new LoginFinding(
                        5,
                        5,
                        NOW.plusNanos(1),
                        LoginFinding.Threshold.ACCOUNT_CLIENT_ATTEMPTS
                ))
        );
    }

    @Test
    void bundledTranslationsContainEveryThrottleReasonAndTimeFormat() {
        YamlConfiguration translations = YamlConfiguration.loadConfiguration(
                new InputStreamReader(
                        Objects.requireNonNull(
                                this.getClass().getResourceAsStream("/translations/en.yml")
                        ),
                        StandardCharsets.UTF_8
                )
        );

        assertTrue(translations.isString("kicked_message.throttled.connection_attempts"));
        assertTrue(translations.isString("kicked_message.throttled.login_attempts"));
        assertTrue(translations.isString("kicked_message.throttled.global_limit"));
        assertTrue(translations.isConfigurationSection("time_format.unit"));
        assertTrue(translations.isString("time_format.join.two"));
        assertTrue(translations.isString("time_format.join.three"));
    }

    private String format_(Finding cause) {
        Decision.Throttled throttled = new Decision.Throttled(
                new Penalty(NetworkKey.unresolved(), cause.retryAt(), cause),
                List.of(cause)
        );
        return this.formatter_.format(this.player_, throttled);
    }

    private static Translator translator_(Map<String, String> translations) {
        return translator_("", translations);
    }

    private static Translator translator_(String prefix, Map<String, String> translations) {
        return (Translator) Proxy.newProxyInstance(
                Translator.class.getClassLoader(),
                new Class<?>[] {Translator.class},
                (_, method, arguments) -> switch (method.getName()) {
                    case "getChild" -> translator_(
                            key_(prefix, (String) arguments[0]),
                            translations
                    );
                    case "translate" -> translationJob_(
                            key_(prefix, (String) arguments[1]),
                            translations
                    );
                    default -> defaultValue_(method.getReturnType());
                }
        );
    }

    private static PlayerTranslationJob translationJob_(
            String key,
            Map<String, String> translations
    ) {
        Map<String, Object> arguments = new HashMap<>();
        String[] fallback = new String[1];
        return (PlayerTranslationJob) Proxy.newProxyInstance(
                PlayerTranslationJob.class.getClassLoader(),
                new Class<?>[] {PlayerTranslationJob.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "argument" -> {
                        arguments.put((String) values[0], values[1]);
                        yield proxy;
                    }
                    case "orDefault" -> {
                        fallback[0] = (String) values[0];
                        yield proxy;
                    }
                    case "message" -> render_(
                            translations.getOrDefault(key, fallback[0]),
                            arguments
                    );
                    default -> defaultValue_(method.getReturnType());
                }
        );
    }

    private static String render_(String template, Map<String, Object> arguments) {
        String rendered = template;
        for (Map.Entry<String, Object> argument : arguments.entrySet()) {
            rendered = rendered.replace(
                    "{" + argument.getKey() + "}",
                    String.valueOf(argument.getValue())
            );
        }
        return rendered;
    }

    private static String key_(String prefix, String child) {
        return prefix.isEmpty() ? child : prefix + "." + child;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy_(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (_, method, _) -> defaultValue_(method.getReturnType())
        );
    }

    private static Object defaultValue_(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
