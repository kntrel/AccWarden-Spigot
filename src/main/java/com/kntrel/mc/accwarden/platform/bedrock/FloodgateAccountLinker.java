package com.kntrel.mc.accwarden.platform.bedrock;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountService;
import com.kntrel.mc.accwarden.account.link.AccountLinkResult;
import com.kntrel.mc.accwarden.account.link.AccountLinker;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class FloodgateAccountLinker implements AccountLinker {

    private final AccWarden plugin_;
    private final AccountService accountService_;
    private final FloodgateApi floodgateApi_;

    public FloodgateAccountLinker(AccWarden plugin) {
        this(plugin, FloodgateApi.getInstance());
    }

    FloodgateAccountLinker(AccWarden plugin, FloodgateApi floodgateApi) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.accountService_ = this.plugin_.getAccountService();
        this.floodgateApi_ = Objects.requireNonNull(floodgateApi, "floodgateApi");
    }

    @Override
    public AccountLinkResult link(Player player) {
        Objects.requireNonNull(player, "player");
        if (!this.plugin_.getAccWardenConfig().playerNameAutoLinking()) {
            this.plugin_.getLogger().fine("Floodgate account linking skipped for " + player.getName() + ": playerNameAutoLinking is disabled.");
            return AccountLinkResult.NO_LINK;
        }

        Optional<Account> account = this.accountService_.getByName(player.getName()).stream().findFirst();
        if (account.isEmpty()) {
            this.plugin_.getLogger().fine("Floodgate account linking skipped for " + player.getName() + ": no account exists with that name.");
            return AccountLinkResult.NO_LINK;
        }
        if (account.get().hasJava() && account.get().hasBedrock()) {
            this.plugin_.getLogger().fine("Floodgate account linking skipped for " + player.getName() + ": account already has Java and Bedrock UUIDs.");
            return AccountLinkResult.ALREADY_LINKED;
        }

        if (this.isBedrockPlayer_(player)) {
            return this.linkBedrockPlayer_(account.get(), player);
        }
        return this.linkJavaPlayer_(account.get(), player);
    }

    private AccountLinkResult linkBedrockPlayer_(Account account, Player player) {
        UUID bedrockUuid = BedrockPlayerIds.accountUuid(this.floodgateApi_, player);
        if (account.getBedrockUuid().filter(bedrockUuid::equals).isPresent()) {
            return AccountLinkResult.ALREADY_LINKED;
        }

        Optional<UUID> javaUuid = account.getJavaUuid();
        if (javaUuid.isEmpty()) {
            this.plugin_.getLogger().fine("Floodgate account linking skipped for Bedrock player " + player.getName() + ": matching account has no Java UUID.");
            return AccountLinkResult.NO_LINK;
        }

        UUID expectedJavaUuid = this.javaUuidFor_(account.getName());
        if (!javaUuid.get().equals(expectedJavaUuid)) {
            this.plugin_.getLogger().log(
                    Level.WARNING,
                    "Floodgate account linking skipped for Bedrock player {0}: account Java UUID is {1}, but expected offline Java UUID for name {2} is {3}.",
                    new Object[] { player.getName(), javaUuid.get(), account.getName(), expectedJavaUuid }
            );
            return AccountLinkResult.NO_LINK;
        }

        this.linkThroughFloodgate_(bedrockUuid, javaUuid.get(), account.getName());
        account.linkBedrock(bedrockUuid);
        account.save();
        this.plugin_.getLogger().info("Linked Bedrock UUID " + bedrockUuid + " to Java account " + account.getName() + " through Floodgate.");
        return AccountLinkResult.LINKED_TO_BEDROCK;
    }

    private AccountLinkResult linkJavaPlayer_(Account account, Player player) {
        UUID javaUuid = player.getUniqueId();
        if (account.getJavaUuid().filter(javaUuid::equals).isPresent()) {
            return AccountLinkResult.ALREADY_LINKED;
        }

        Optional<UUID> bedrockUuid = account.getBedrockUuid();
        if (bedrockUuid.isEmpty()) {
            this.plugin_.getLogger().fine("Floodgate account linking skipped for Java player " + player.getName() + ": matching account has no Bedrock UUID.");
            return AccountLinkResult.NO_LINK;
        }

        UUID expectedJavaUuid = this.javaUuidFor_(account.getName());
        if (!javaUuid.equals(expectedJavaUuid)) {
            this.plugin_.getLogger().log(
                    Level.WARNING,
                    "Floodgate account linking skipped for Java player {0}: joining UUID is {1}, but expected offline Java UUID for name {2} is {3}.",
                    new Object[] { player.getName(), javaUuid, account.getName(), expectedJavaUuid }
            );
            return AccountLinkResult.NO_LINK;
        }

        this.linkThroughFloodgate_(bedrockUuid.get(), javaUuid, account.getName());
        account.linkJava(javaUuid);
        account.save();
        this.plugin_.getLogger().info("Linked Java account " + account.getName() + " to Bedrock UUID " + bedrockUuid.get() + " through Floodgate.");
        return AccountLinkResult.LINKED_TO_JAVA;
    }

    private void linkThroughFloodgate_(UUID bedrockUuid, UUID javaUuid, String javaUsername) {
        this.floodgateApi_.getPlayerLink()
                .linkPlayer(bedrockUuid, javaUuid, javaUsername)
                .join();
    }

    private boolean isBedrockPlayer_(Player player) {
        return this.floodgateApi_.isFloodgatePlayer(player.getUniqueId());
    }

    private UUID javaUuidFor_(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
