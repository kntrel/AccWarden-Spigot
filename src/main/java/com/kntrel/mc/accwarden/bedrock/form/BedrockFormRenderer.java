package com.kntrel.mc.accwarden.bedrock.form;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.form.Form;
import com.kntrel.mc.accwarden.form.FormAction;
import com.kntrel.mc.accwarden.form.FormActionRole;
import com.kntrel.mc.accwarden.form.FormElement;
import com.kntrel.mc.accwarden.form.FormHandle;
import com.kntrel.mc.accwarden.form.FormInput;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.form.FormResponse;
import com.kntrel.mc.accwarden.form.FormResponseHandler;
import com.kntrel.mc.accwarden.form.FormText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.EventSubscriber;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BedrockFormRenderer implements FormRenderer {

    private static final long SESSION_JOIN_TIMEOUT_TICKS = 30L * 20L;

    private final AccWarden plugin_;
    private final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();
    private final Map<UUID, BedrockFormHandle> activeForms_ = new HashMap<>();
    private final Map<UUID, PendingSessionJoin> pendingSessionJoins_ = new HashMap<>();
    private EventRegistrar geyserEventRegistrar_;
    private EventSubscriber<EventRegistrar, SessionJoinEvent> sessionJoinSubscriber_;

    public BedrockFormRenderer(AccWarden plugin) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
        this.plugin_.getServer().getPluginManager().registerEvents(new Listener(this), this.plugin_);
        this.startGeyserListenerIfNeeded_();
    }

    @Override
    public FormHandle show(Player player, Form form, FormResponseHandler handler) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(handler, "handler");

        this.findForm_(player).ifPresent(BedrockFormHandle::close);

        BedrockFormHandle handle = new BedrockFormHandle(this, player, form, handler);
        this.activeForms_.put(player.getUniqueId(), handle);

        PendingSessionJoin pending = this.pendingSessionJoins_.get(player.getUniqueId());
        if (pending != null) {
            pending.attach(handle, this.timeoutTask_(player.getUniqueId(), pending));
            return handle;
        }

        this.runSync_(handle::send);
        return handle;
    }

    private BukkitTask timeoutTask_(UUID playerId, PendingSessionJoin pending) {
        return Bukkit.getScheduler().runTaskLater(
                this.plugin_,
                () -> this.timeoutSessionJoin_(playerId, pending),
                SESSION_JOIN_TIMEOUT_TICKS
        );
    }

    private void timeoutSessionJoin_(UUID playerId, PendingSessionJoin pending) {
        if (!this.pendingSessionJoins_.remove(playerId, pending)) {
            return;
        }
        pending.cancelTimeout();
        if (pending.form_ != null) {
            this.plugin_.getLogger().fine("Timed out waiting for Geyser join event for Bedrock player '" + pending.form_.player_.getName() + "'.");
            pending.form_.timeout();
        }
    }

    private Optional<BedrockFormHandle> findForm_(Player player) {
        return Optional.ofNullable(this.activeForms_.get(player.getUniqueId()));
    }

    private void remove_(BedrockFormHandle handle) {
        UUID playerId = handle.player_.getUniqueId();
        this.activeForms_.remove(playerId, handle);
        PendingSessionJoin pending = this.pendingSessionJoins_.get(playerId);
        if (pending != null && pending.form_ == handle) {
            pending.form_ = null;
            pending.cancelTimeout();
        }
    }

    private void startGeyserListenerIfNeeded_() {
        if (this.sessionJoinSubscriber_ != null) {
            return;
        }
        this.geyserEventRegistrar_ = EventRegistrar.of(this.plugin_);
        this.sessionJoinSubscriber_ = GeyserApi.api()
                .eventBus()
                .subscribe(this.geyserEventRegistrar_, SessionJoinEvent.class, this::onGeyserSessionJoin_);
    }

    private void onGeyserSessionJoin_(SessionJoinEvent event) {
        UUID playerId = event.connection().javaUuid();
        Bukkit.getScheduler().runTask(this.plugin_, () -> this.flushPendingSessionJoin_(playerId));
    }

    private void flushPendingSessionJoin_(UUID playerId) {
        PendingSessionJoin pending = this.pendingSessionJoins_.remove(playerId);
        if (pending == null) {
            return;
        }
        pending.cancelTimeout();
        if (pending.form_ != null) {
            pending.form_.send();
        }
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private static final class PendingSessionJoin {

        private BedrockFormHandle form_;
        private BukkitTask timeoutTask_;

        void attach(BedrockFormHandle form, BukkitTask timeoutTask) {
            this.cancelTimeout();
            this.form_ = form;
            this.timeoutTask_ = timeoutTask;
        }

        void cancelTimeout() {
            if (this.timeoutTask_ == null) {
                return;
            }
            this.timeoutTask_.cancel();
            this.timeoutTask_ = null;
        }
    }

    private void trackBukkitJoinedBedrockPlayer_(Player player) {
        if (!this.floodgateApi_.isFloodgatePlayer(player.getUniqueId())) {
            return;
        }
        this.pendingSessionJoins_.putIfAbsent(player.getUniqueId(), new PendingSessionJoin());
    }

    private void releasePlayer_(Player player) {
        PendingSessionJoin pending = this.pendingSessionJoins_.remove(player.getUniqueId());
        if (pending != null) {
            pending.cancelTimeout();
        }
        this.findForm_(player).ifPresent(BedrockFormHandle::cancel);
    }

    private static final class BedrockFormHandle implements FormHandle {

        private final BedrockFormRenderer renderer_;
        private final Player player_;
        private final Form form_;
        private final FormResponseHandler handler_;
        private boolean active_ = true;
        private boolean sent_ = false;

        BedrockFormHandle(BedrockFormRenderer renderer, Player player, Form form, FormResponseHandler handler) {
            this.renderer_ = renderer;
            this.player_ = player;
            this.form_ = form;
            this.handler_ = handler;
        }

        @Override
        public void close() {
            this.closeSilently_();
        }

        @Override
        public void cancel() {
            this.respond_(FormResponse.closed());
        }

        void timeout() {
            this.respond_(FormResponse.closed());
        }

        void send() {
            if (!this.active_ || !this.player_.isOnline()) {
                return;
            }

            boolean sent = this.usesModal_()
                    ? this.sendModal_()
                    : this.sendCustom_();
            this.sent_ = sent;
            if (!sent) {
                this.respond_(FormResponse.invalid());
            }
        }

        private boolean sendCustom_() {
            CustomForm.Builder builder = CustomForm.builder()
                    .title(this.form_.title())
                    .closedResultHandler(() -> this.respond_(FormResponse.closed()))
                    .invalidResultHandler(() -> this.respond_(FormResponse.invalid()));

            Map<String, Integer> inputIndexes = new HashMap<>();
            int componentIndex = 0;
            for (FormElement element : this.form_.elements()) {
                if (element instanceof FormText text) {
                    builder.label(text.text());
                    componentIndex++;
                    continue;
                }
                if (element instanceof FormInput input) {
                    builder.input(input.label(), input.placeholder(), input.defaultValue());
                    inputIndexes.put(input.id(), componentIndex);
                    componentIndex++;
                }
            }

            Optional<FormAction> action = this.primaryAction_();
            builder.validResultHandler((CustomFormResponse response) -> {
                if (action.isEmpty()) {
                    this.respond_(FormResponse.invalid());
                    return;
                }
                this.respond_(FormResponse.action(action.get().id(), this.values_(response, inputIndexes)));
            });

            return this.renderer_.floodgateApi_.sendForm(this.player_.getUniqueId(), builder);
        }

        private boolean sendModal_() {
            List<FormAction> actions = this.form_.actions();
            ModalForm.Builder builder = ModalForm.builder()
                    .content(this.modalContent_())
                    .button1(actions.get(0).label())
                    .button2(actions.get(1).label())
                    .closedResultHandler(() -> this.respond_(FormResponse.closed()))
                    .invalidResultHandler(() -> this.respond_(FormResponse.invalid()))
                    .validResultHandler((ModalFormResponse response) -> this.respond_(FormResponse.action(
                            response.clickedFirst() ? actions.get(0).id() : actions.get(1).id(),
                            Map.of()
                    )));

            return this.renderer_.floodgateApi_.sendForm(this.player_.getUniqueId(), builder);
        }

        private boolean usesModal_() {
            return this.form_.elements().stream().noneMatch(FormInput.class::isInstance)
                    && this.form_.actions().size() == 2;
        }

        private String modalContent_() {
            StringBuilder content = new StringBuilder();
            for (FormElement element : this.form_.elements()) {
                if (element instanceof FormText text) {
                    content.append(text.text()).append("\n");
                }
            }
            return content.toString();
        }

        private Map<String, String> values_(CustomFormResponse response, Map<String, Integer> inputIndexes) {
            Map<String, String> values = new HashMap<>();
            inputIndexes.forEach((id, index) -> values.put(id, response.asInput(index)));
            return values;
        }

        private Optional<FormAction> primaryAction_() {
            return this.form_.actions()
                    .stream()
                    .filter(action -> action.role().equals(FormActionRole.PRIMARY))
                    .findFirst()
                    .or(() -> this.form_.actions().stream().findFirst());
        }

        private void closeSilently_() {
            if (!this.active_) {
                return;
            }
            this.active_ = false;
            this.renderer_.remove_(this);
            if (this.sent_) {
                this.renderer_.floodgateApi_.closeForm(this.player_.getUniqueId());
            }
        }

        private void respond_(FormResponse response) {
            if (!this.active_) {
                return;
            }
            this.active_ = false;
            this.renderer_.remove_(this);
            this.renderer_.runSync_(() -> this.handler_.onResponse(response));
        }
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final BedrockFormRenderer renderer_;

        Listener(BedrockFormRenderer renderer) {
            this.renderer_ = renderer;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        void onPlayerJoin(PlayerJoinEvent event) {
            this.renderer_.trackBukkitJoinedBedrockPlayer_(event.getPlayer());
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            this.renderer_.releasePlayer_(event.getPlayer());
        }
    }
}
