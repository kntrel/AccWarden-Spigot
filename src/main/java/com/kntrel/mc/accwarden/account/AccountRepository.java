package com.kntrel.mc.accwarden.account;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> getByJavaId(UUID id);

    Optional<Account> getByBedrockId(UUID id);

    default Optional<Account> get(Player player) {
        return this.getByJavaId(player.getUniqueId());
    }

    Set<Account> getAll();

    Set<Account> getByName(String name);

    default boolean existsByJavaId(UUID id) {
        return this.getByJavaId(id).isPresent();
    }

    default boolean existsByBedrockId(UUID id) {
        return this.getByBedrockId(id).isPresent();
    }

    default boolean exists(Player player) {
        return this.get(player).isPresent();
    }

    void save(Account account);

    void delete(Account account);
}
