package com.kntrel.mc.accwarden;

import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;

public record AccWardenConfig(
        String defaultLanguage,
        HoldWorld holdWorld,
        boolean playerNameAutoLinking,
        int sessionHoldTime,
        int passwordMinSize,
        int passwordMaxSize,
        Gateway securityPolicies
) {

    public static final AccWardenConfig DEFAULT = new AccWardenConfig(
            "en",
            HoldWorld.DEFAULT,
            false,
            300,
            4,
            12,
            Gateway.DEFAULT
    );

    public static AccWardenConfig load(ConfigurationSection config) {
        ConfigurationSection securityPolicies = config.getConfigurationSection(
                "security_policies"
        );
        if (securityPolicies == null) {
            securityPolicies = config.getConfigurationSection("gatekeeping");
        }

        return new AccWardenConfig(
                config.getString("defaultLanguage", DEFAULT.defaultLanguage()),
                loadHoldWorld_(config),
                config.getBoolean("playerNameAutoLinking", DEFAULT.playerNameAutoLinking()),
                config.getInt("sessions.holdTime", DEFAULT.sessionHoldTime()),
                config.getInt("password_format.min_length", DEFAULT.passwordMinSize()),
                config.getInt("password_format.max_length", DEFAULT.passwordMaxSize()),
                Gateway.load(securityPolicies)
        );
    }

    private static HoldWorld loadHoldWorld_(ConfigurationSection config) {
        return HoldWorld.load(config.getConfigurationSection("hold_world"));
    }

    public record HoldWorld(
            boolean enabled,
            String name,
            String dimension
    ) {

        public static final HoldWorld DEFAULT = new HoldWorld(
                false,
                "accwarden_hold",
                "NORMAL"
        );

        public HoldWorld {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dimension, "dimension");
        }

        public static HoldWorld load(@Nullable ConfigurationSection config) {
            if (config == null) {
                return DEFAULT;
            }
            return new HoldWorld(
                    config.getBoolean("enabled", DEFAULT.enabled()),
                    config.getString("name", DEFAULT.name()),
                    config.getString("dimension", DEFAULT.dimension())
            );
        }
    }

    public record Gateway(
            int multiClientAccountWarningCount,
            ClientConnectionsLimit clientConnectionsLimit,
            ClientLoginLimit clientLoginLimit
    ) {

        public static final Gateway DEFAULT = new Gateway(
                5,
                new ClientConnectionsLimit(
                        true,
                        Duration.ofMinutes(1),
                        10,
                        1_000,
                        Duration.ofMinutes(1)
                ),
                new ClientLoginLimit(
                        true,
                        Duration.ofMinutes(1),
                        3,
                        4,
                        5,
                        Duration.ofMinutes(1)
                )
        );

        public Gateway {
            requirePositive_(
                    multiClientAccountWarningCount,
                    "multi_client_account_warning_count"
            );
            Objects.requireNonNull(clientConnectionsLimit, "client_connections_limit");
            Objects.requireNonNull(clientLoginLimit, "client_login_limit");
        }

        public static Gateway load(@Nullable ConfigurationSection config) {
            if (config == null) {
                return DEFAULT;
            }

            Duration clientExpiration = duration_(
                    config,
                    "client_connections_limit.expiration_seconds",
                    duration_(
                            config,
                            "client.windowSeconds",
                            DEFAULT.clientConnectionsLimit().expiration()
                    )
            );
            Duration loginExpiration = duration_(
                    config,
                    "client_login_limit.expiration_seconds",
                    duration_(config, "login.windowSeconds", clientExpiration)
            );

            return new Gateway(
                    config.getInt(
                            "multi_client_account_warning_count",
                            config.getInt(
                                    "account.distinctClientLimit",
                                    DEFAULT.multiClientAccountWarningCount()
                            )
                    ),
                    new ClientConnectionsLimit(
                            config.getBoolean(
                                    "client_connections_limit.enabled",
                                    config.getBoolean(
                                            "client.attempt_limit.enabled",
                                            DEFAULT.clientConnectionsLimit().enabled()
                                    )
                            ),
                            clientExpiration,
                            config.getInt(
                                    "client_connections_limit.limit",
                                    config.getInt(
                                            "client.attempt_limit.limit",
                                            config.getInt(
                                                    "client.perClientLimit",
                                                    DEFAULT.clientConnectionsLimit().limit()
                                            )
                                    )
                            ),
                            config.getInt(
                                    "client_connections_limit.global_limit",
                                    previousConnectionLimit_(config)
                            ),
                            duration_(
                                    config,
                                    "client_connections_limit.penalty_seconds",
                                    duration_(
                                            config,
                                            "client.attempt_limit.penaltySeconds",
                                            duration_(
                                                    config,
                                                    "client.penaltySeconds",
                                                    clientExpiration
                                            )
                                    )
                            )
                    ),
                    new ClientLoginLimit(
                            config.getBoolean(
                                    "client_login_limit.enabled",
                                    config.getBoolean(
                                            "login.attempt_limit.enabled",
                                            config.getBoolean(
                                                    "login.clientThrottling.enabled",
                                                    DEFAULT.clientLoginLimit().enabled()
                                            )
                                    )
                            ),
                            loginExpiration,
                            config.getInt(
                                    "client_login_limit.odd",
                                    config.getInt(
                                            "login.attempt_limit.odd",
                                            DEFAULT.clientLoginLimit().odd()
                                    )
                            ),
                            config.getInt(
                                    "client_login_limit.warn",
                                    config.getInt(
                                            "login.attempt_limit.warn",
                                            DEFAULT.clientLoginLimit().warn()
                                    )
                            ),
                            config.getInt(
                                    "client_login_limit.limit",
                                    config.getInt(
                                            "login.attempt_limit.limit",
                                            previousLoginLimit_(config)
                                    )
                            ),
                            duration_(
                                    config,
                                    "client_login_limit.penalty_seconds",
                                    duration_(
                                            config,
                                            "login.attempt_limit.penaltySeconds",
                                            duration_(
                                                    config,
                                                    "login.penaltySeconds",
                                                    loginExpiration
                                            )
                                    )
                            )
                    )
            );
        }

        public record ClientConnectionsLimit(
                boolean enabled,
                Duration expiration,
                int limit,
                int globalLimit,
                Duration penalty
        ) {

            public ClientConnectionsLimit {
                expiration = requirePositiveDuration_(
                        expiration,
                        "client_connections_limit.expiration_seconds"
                );
                requirePositive_(limit, "client_connections_limit.limit");
                requirePositive_(globalLimit, "client_connections_limit.global_limit");
                penalty = requirePositiveDuration_(
                        penalty,
                        "client_connections_limit.penalty_seconds"
                );
            }
        }

        public record ClientLoginLimit(
                boolean enabled,
                Duration expiration,
                int odd,
                int warn,
                int limit,
                Duration penalty
        ) {

            public ClientLoginLimit {
                expiration = requirePositiveDuration_(
                        expiration,
                        "client_login_limit.expiration_seconds"
                );
                requirePositive_(odd, "client_login_limit.odd");
                requirePositive_(warn, "client_login_limit.warn");
                requirePositive_(limit, "client_login_limit.limit");
                if (odd > warn || warn > limit) {
                    throw new IllegalArgumentException(
                            "client_login_limit must satisfy odd <= warn <= limit."
                    );
                }
                penalty = requirePositiveDuration_(
                        penalty,
                        "client_login_limit.penalty_seconds"
                );
            }
        }

        private static int previousLoginLimit_(ConfigurationSection config) {
            int fallback = DEFAULT.clientLoginLimit().limit();
            return config.getInt(
                    "login.clientThrottling.failedLoginLimit",
                    config.getInt(
                            "login.failedLoginsPerClientLimit",
                            config.getInt("login.perLoginLimit", fallback)
                    )
            );
        }

        private static int previousConnectionLimit_(ConfigurationSection config) {
            int maxRecords = config.getInt(
                    "client.maxRecords",
                    DEFAULT.clientConnectionsLimit().globalLimit()
            );
            int previousLimit = Math.min(
                    maxRecords,
                    config.getInt("client.globalLimit", maxRecords)
            );
            return config.getInt("client.connection_limit", previousLimit);
        }

        private static Duration duration_(
                ConfigurationSection config,
                String path,
                Duration fallback
        ) {
            return Duration.ofSeconds(config.getLong(path, fallback.toSeconds()));
        }

        private static Duration requirePositiveDuration_(Duration duration, String path) {
            Objects.requireNonNull(duration, path);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(path + " must be greater than zero.");
            }
            return duration;
        }

        private static void requirePositive_(int value, String path) {
            if (value < 1) {
                throw new IllegalArgumentException(path + " must be at least 1.");
            }
        }
    }
}
