package com.kntrel.mc.accwarden.java.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.form.Form;
import com.kntrel.mc.accwarden.form.FormAction;
import com.kntrel.mc.accwarden.form.FormElement;
import com.kntrel.mc.accwarden.form.FormHandle;
import com.kntrel.mc.accwarden.form.FormInput;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.form.FormResponse;
import com.kntrel.mc.accwarden.form.FormResponseHandler;
import com.kntrel.mc.accwarden.form.FormText;
import com.kntrel.mc.accwarden.form.FormTextTone;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.dialog.Dialog;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.DialogInput;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerCustomClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JavaFormRenderer implements FormRenderer {

    private static final int DIALOG_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;

    private final AccWarden plugin_;
    private final Map<UUID, JavaFormHandle> activeForms_ = new HashMap<>();
    private Listener listener_;

    public JavaFormRenderer(AccWarden plugin) {
        this.plugin_ = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public FormHandle show(Player player, Form form, FormResponseHandler handler) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(handler, "handler");

        this.findForm_(player).ifPresent(JavaFormHandle::close);

        JavaFormHandle handle = new JavaFormHandle(this, player, form, handler);
        this.activeForms_.put(player.getUniqueId(), handle);
        this.startListenerIfNeeded_();
        this.runSync_(handle::show);
        return handle;
    }

    private Optional<JavaFormHandle> findForm_(Player player) {
        return Optional.ofNullable(this.activeForms_.get(player.getUniqueId()));
    }

    private void remove_(JavaFormHandle handle) {
        this.activeForms_.remove(handle.player_.getUniqueId(), handle);
        this.stopIfIdle_();
    }

    private void startListenerIfNeeded_() {
        if (this.listener_ != null) {
            return;
        }
        this.listener_ = new Listener(this);
        this.plugin_.getServer().getPluginManager().registerEvents(this.listener_, this.plugin_);
    }

    private void stopIfIdle_() {
        if (!this.activeForms_.isEmpty() || this.listener_ == null) {
            return;
        }
        HandlerList.unregisterAll(this.listener_);
        this.listener_ = null;
    }

    private void runSync_(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        this.plugin_.getServer().getScheduler().runTask(this.plugin_, runnable);
    }

    private static final class JavaFormHandle implements FormHandle {

        private final JavaFormRenderer renderer_;
        private final Player player_;
        private final Form form_;
        private final FormResponseHandler handler_;
        private final Map<NamespacedKey, String> actionIds_ = new HashMap<>();
        private boolean active_ = true;

        JavaFormHandle(JavaFormRenderer renderer, Player player, Form form, FormResponseHandler handler) {
            this.renderer_ = renderer;
            this.player_ = player;
            this.form_ = form;
            this.handler_ = handler;
            form.actions().forEach(action -> this.actionIds_.put(this.actionKey_(action), action.id()));
        }

        @Override
        public void close() {
            this.closeSilently_();
        }

        @Override
        public void cancel() {
            this.respond_(FormResponse.closed());
        }

        void show() {
            if (!this.active_ || !this.player_.isOnline()) {
                return;
            }
            this.player_.showDialog(this.dialog_());
        }

        boolean isAction(NamespacedKey key) {
            return this.actionIds_.containsKey(key);
        }

        void click(NamespacedKey key, JsonElement data) {
            String actionId = this.actionIds_.get(key);
            if (actionId == null) {
                return;
            }
            this.respond_(FormResponse.action(actionId, this.values_(data)));
        }

        private void closeSilently_() {
            if (!this.active_) {
                return;
            }
            this.active_ = false;
            this.renderer_.remove_(this);
            this.clearDialog_();
        }

        private void respond_(FormResponse response) {
            if (!this.active_) {
                return;
            }
            this.active_ = false;
            this.renderer_.remove_(this);
            this.clearDialog_();
            this.handler_.onResponse(response);
        }

        private void clearDialog_() {
            if (this.player_.isOnline()) {
                this.renderer_.runSync_(this.player_::clearDialog);
            }
        }

        private Dialog dialog_() {
            DialogBase base = new DialogBase(this.component_(this.form_.title()));
            base.body(this.body_())
                    .inputs(this.inputs_())
                    .canCloseWithEscape(this.form_.canClose())
                    .pause(false)
                    .afterAction(DialogBase.AfterAction.NONE);

            return new MultiActionDialog(
                    base,
                    this.actions_(),
                    1,
                    null
            );
        }

        private List<DialogBody> body_() {
            List<DialogBody> body = new ArrayList<>();
            for (FormElement element : this.form_.elements()) {
                if (element instanceof FormText text && !text.text().isBlank()) {
                    body.add(new PlainMessageBody(
                            this.component_(text.text(), this.color_(text.tone())),
                            DIALOG_WIDTH
                    ));
                }
            }
            return body;
        }

        private List<DialogInput> inputs_() {
            List<DialogInput> inputs = new ArrayList<>();
            for (FormElement element : this.form_.elements()) {
                if (element instanceof FormInput input) {
                    inputs.add(new TextInput(
                            input.id(),
                            DIALOG_WIDTH,
                            this.component_(input.label()),
                            true,
                            input.defaultValue(),
                            input.maxLength()
                    ));
                }
            }
            return inputs;
        }

        private List<ActionButton> actions_() {
            List<ActionButton> buttons = new ArrayList<>();
            for (FormAction action : this.form_.actions()) {
                buttons.add(new ActionButton(
                        this.component_(action.label()),
                        null,
                        BUTTON_WIDTH,
                        new CustomClickAction(this.actionKey_(action).toString())
                ));
            }
            return buttons;
        }

        private NamespacedKey actionKey_(FormAction action) {
            return new NamespacedKey(
                    this.renderer_.plugin_,
                    "form_" + this.keyPart_(this.form_.id()) + "_" + this.keyPart_(action.id())
            );
        }

        private String keyPart_(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            return normalized.isBlank() ? "unnamed" : normalized;
        }

        private Map<String, String> values_(JsonElement data) {
            Map<String, String> values = new HashMap<>();
            for (FormElement element : this.form_.elements()) {
                if (element instanceof FormInput input) {
                    values.put(input.id(), this.readInput_(data, input.id()));
                }
            }
            return values;
        }

        private String readInput_(JsonElement data, String key) {
            if (data == null || !data.isJsonObject()) {
                return "";
            }

            JsonObject object = data.getAsJsonObject();
            Optional<String> directValue = this.readString_(object, key);
            if (directValue.isPresent()) {
                return directValue.get();
            }

            for (String nestedKey : List.of("inputs", "values", "data")) {
                Optional<String> nestedValue = this.readObject_(object, nestedKey)
                        .flatMap(nested -> this.readString_(nested, key));
                if (nestedValue.isPresent()) {
                    return nestedValue.get();
                }
            }

            return "";
        }

        private Optional<JsonObject> readObject_(JsonObject object, String key) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonObject()) {
                return Optional.empty();
            }
            return Optional.of(value.getAsJsonObject());
        }

        private Optional<String> readString_(JsonObject object, String key) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                return Optional.empty();
            }
            return Optional.of(value.getAsString());
        }

        private BaseComponent component_(String text) {
            return new TextComponent(text);
        }

        private BaseComponent component_(String text, ChatColor color) {
            TextComponent component = new TextComponent(text);
            component.setColor(color);
            return component;
        }

        private ChatColor color_(FormTextTone tone) {
            return switch (tone) {
                case NORMAL -> ChatColor.WHITE;
                case ERROR -> ChatColor.RED;
                case WARNING -> ChatColor.YELLOW;
                case SUCCESS -> ChatColor.GREEN;
            };
        }
    }

    private static final class Listener implements org.bukkit.event.Listener {

        private final JavaFormRenderer renderer_;

        Listener(JavaFormRenderer renderer) {
            this.renderer_ = renderer;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerCustomClick(PlayerCustomClickEvent event) {
            this.renderer_.findForm_(event.getPlayer())
                    .filter(form -> form.isAction(event.getId()))
                    .ifPresent(form -> form.click(event.getId(), event.getData()));
        }

        @EventHandler
        void onPlayerQuit(PlayerQuitEvent event) {
            this.renderer_.findForm_(event.getPlayer()).ifPresent(JavaFormHandle::cancel);
        }
    }
}