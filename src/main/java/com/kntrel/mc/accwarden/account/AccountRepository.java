package com.kntrel.mc.accwarden.account;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> getByUUID(UUID id);

    default Optional<Account> get(Player player) {
        return this.getByUUID(player.getUniqueId());
    }

    Set<Account> getAll();

    Set<Account> getByName(String name);

    default boolean existsByUUID(UUID id) {
        return this.getByUUID(id).isPresent();
    }

    default boolean exists(Player player) {
        return this.get(player).isPresent();
    }

    void save(Account account);

    void delete(Account account);
}
