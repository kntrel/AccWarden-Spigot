package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.AccWarden;
import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.account.AccountRepository;
import com.kntrel.mc.accwarden.account.exception.PasswordTooLongException;
import com.kntrel.mc.accwarden.account.exception.PasswordTooShortException;
import com.kntrel.mc.accwarden.io.Config;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.geysermc.cumulus.CustomForm;
import org.geysermc.cumulus.ModalForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

public class BedrockSessionHandler extends SessionHandler {

    //STATIC METHODS
    public static boolean isBedrock(Player player) {
        return BedrockSessionHandler.isBedrock(player.getUniqueId());
    }
    public static boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    //FIELDS
    private final FloodgateApi floodgateApi_ = FloodgateApi.getInstance();
    private final LinkedList<BedrockSessionHandler.Login> logins_ = new LinkedList<>();
    private BedrockSessionHandler.Listener listener_;

    public BedrockSessionHandler(AccountRepository repository, SessionHolder sessionHolder, AccWarden plugin) {
        super(repository, sessionHolder, plugin);
    }

    //METHODS
    @Override
    public void handle(Player player) {
        FloodgatePlayer fgPlayer = this.floodgateApi_.getPlayer(player.getUniqueId());
        if (fgPlayer == null) {
            LoginManager.reset(player);
            return;
        }

        Account account = this.accountRepository.retrieve(player);

        LoginMode mode;
        if (account.hasBedrock()) {
            this.log("Bedrock player with UUID '" + account.getId() + "' is already registered with Bedrock access. Logging in.");
            LoginManager.logIn(player,account);
            player.sendMessage(ChatColor.GREEN + this.plugin.getLangProvider().getEntry(player,"info.logged_in"));
            return;
        } else if (account.hasJava()) {
            mode = LoginMode.NEW_IN_PLATFORM;
        } else {
            mode = LoginMode.NEW;
        }

        LoginManager.reset(player);
        this.addLogin_(new Login(this, player, mode));
    }

    //PRIVATE METHODS
    private void addLogin_(BedrockSessionHandler.Login login) {
        this.logins_.add(login);
        if (this.listener_ == null) {
            this.listener_ = new BedrockSessionHandler.Listener(this);
            this.plugin.getServer().getPluginManager().registerEvents(this.listener_, this.plugin);
        }

    }
    private void removeLogin_(BedrockSessionHandler.Login login) {
        this.logins_.remove(login);
        if (this.logins_.isEmpty() && this.listener_ != null) {
            HandlerList.unregisterAll(this.listener_);
            this.listener_ = null;
        }
    }
    private boolean sendRegisterForm_(Player player, Account account, LoginMode mode) {
        BiConsumer<CustomForm, String> responseHandler = (f, s) -> {
            CustomFormResponse response = (CustomFormResponse) f.parseResponse(s);

            if (!response.isCorrect()) {
                BedrockSessionHandler.this.sendRegisterForm_(player,account,mode);
                this.log("Invalid form", Level.WARNING);
                return;
            }

            String  pw1 = response.getInput(1),
                    pw2 = response.getInput(2);

            boolean match = false;
            try {
                match = account.setPassword(pw1, pw2);
            } catch (PasswordTooShortException ex) {
                BedrockSessionHandler.this.sendErrorForm_(player, "Password too short", r -> BedrockSessionHandler.this.sendRegisterForm_(player,account,mode));
            } catch (PasswordTooLongException ex) {
                BedrockSessionHandler.this.sendErrorForm_(player, "Password too long", r -> BedrockSessionHandler.this.sendRegisterForm_(player,account,mode));
            }

            if (!match) {
                BedrockSessionHandler.this.sendErrorForm_(player, "Passwords don't match.", r -> BedrockSessionHandler.this.sendRegisterForm_(player,account,mode));
            } else {
                account.setBedrock(true);
                account.save();
                LoginManager.logIn(player, account);
                player.sendMessage(ChatColor.GREEN + BedrockSessionHandler.this.plugin.getLangProvider().getEntry(player,"info.logged_in"));
            }
        };

        return this.floodgateApi_.sendForm(player.getUniqueId(), CustomForm.builder()
                .label("Welcome to the server! To prevent your user name from being stolen in Java, define a password.")
                .input("Password")
                .input("Confirm password")
                .responseHandler(responseHandler)
        );
    }
    private void sendErrorForm_(Player player, String message, Consumer<ModalFormResponse> responseHandler) {
        this.floodgateApi_.sendForm(player.getUniqueId(), ModalForm.builder()
                .content(message)
                .button1("Accept")
                .responseHandler((f,s) -> responseHandler.accept((ModalFormResponse) f.parseResponse(s)))
        );
    }

    private static class Login {

        //FIELDS
        private final BedrockSessionHandler handler_;
        private final Player player_;
        private final Account account_;
        private final LoginMode mode_;
        private int tries_ = 0;

        //CONSTRUCTORS
        Login(BedrockSessionHandler handler, Player player, LoginMode mode) {
            this.handler_ = handler;
            this.player_ = player;
            this.account_ = this.handler_.accountRepository.retrieve(player);
            this.mode_ = mode;
        }

        //METHODS
        public void sendForm() {
            String basePath = "login.bedrock." + switch (this.mode_) {
                case NEW -> "new"; case NEW_IN_PLATFORM -> "new_in_bedrock"; case EXISTING -> "existing";
            } + ".";
            String placeholder = this.getLangMessage_(basePath + "fields.placeholder");

            CustomForm.Builder formBuilder = CustomForm.builder();
            formBuilder
                    .label(this.getLangMessage_(basePath + "disclosure"))
                    .input(this.getLangMessage_(basePath + "fields.password"), placeholder);
            if (this.mode_.equals(LoginMode.NEW)) {
                formBuilder.input(this.getLangMessage_(basePath + "fields.confirm_password"), placeholder);
            }
            formBuilder.responseHandler((this.mode_.equals(LoginMode.NEW)) ?
                (f, s) -> {
                    CustomFormResponse response = f.parseResponse(s);
                    if (!response.isCorrect()) { this.kick_(); return; }

                    String  pw1 = response.getInput(1),
                            pw2 = response.getInput(2),
                            error = null;

                    boolean match = false;
                    try {
                        match = this.account_.setPassword(pw1, pw2);
                    } catch (PasswordTooShortException ex) {
                        error = this.getLangMessage_("error.invalid_input.too_short", Integer.toString(ex.getMinLength()));
                    } catch (PasswordTooLongException ex) {
                        error = this.getLangMessage_("error.invalid_input.too_long", Integer.toString(ex.getMaxLength()));
                    }

                    if (error != null) {
                        this.sendErrorMessage_(error);
                        return;
                    }

                    if (!match) {
                        this.sendErrorMessage_(this.getLangMessage_("error.invalid_input.no_match"));
                        return;
                    }

                    this.login_();
                } : (f, s) -> {
                    CustomFormResponse response = f.parseResponse(s);
                    if (!response.isCorrect()) {
                        this.kick_();
                        return;
                    }

                    String pw = response.getInput(1);
                    boolean match = this.account_.checkPassword(pw);
                    if (match) {
                        this.login_();
                        return;
                    }

                    this.tries_++;
                    String errorBasePath = "error.incorrect_password.";
                    Config conf = this.handler_.plugin.CONFIG;
                    String errorMessage = this.getLangMessage_(errorBasePath + "fine");
                    if (this.tries_ >= conf.failLoginOdd) {
                        int next = conf.failLoginOdd + conf.failLoginWarn;
                        if (this.tries_ >= next && conf.failLoginAccountLock) {
                            next += conf.failLoginLock;
                            if (this.tries_ >= next && conf.failLoginLock > 0) {
                                this.account_.lock();
                            } else { errorMessage = this.getLangMessage_(errorBasePath + "warn"); }
                        } else { errorMessage = this.getLangMessage_(errorBasePath + "odd"); }
                    }

                    this.sendErrorMessage_(errorMessage);
                }
            );

            this.handler_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
            this.handler_.removeLogin_(this);
        }

        //PRIVATE METHODS
        private void sendErrorMessage_(String... errors) {
            ModalForm.Builder formBuilder = ModalForm.builder();

            StringBuilder errorBuilder = new StringBuilder();
            Arrays.stream(errors).forEach(s -> errorBuilder.append(s).append("\n"));

            formBuilder
                    .content(errorBuilder.toString())
                    .button1(this.getLangMessage_("login.bedrock.error_form.buttonRetry"))
                    .button2(this.getLangMessage_("login.bedrock.error_form.buttonQuit"));
            formBuilder.responseHandler((f, s) -> {
                ModalFormResponse response = f.parseResponse(s);
                if (!(response.isCorrect() && response.getResult())) { this.kick_(); return; }
                this.sendForm();
            });

            this.handler_.floodgateApi_.sendForm(this.player_.getUniqueId(), formBuilder);
        }
        private void kick_() {
           this.player_.kickPlayer(this.getLangMessage_(
                "error.kicked." + ((this.mode_.equals(LoginMode.NEW)) ? "not_registered" : "not_logged")
            ));
        }
        private void login_() {
            this.account_.setBedrock(true);
            this.account_.save();
            LoginManager.logIn(this.player_,this.account_);
            this.player_.sendMessage(ChatColor.GREEN + this.getLangMessage_("info.logged_in"));
        }
        private String getLangMessage_(String path, String... params) {
            return this.handler_.plugin.getLangProvider().getEntry(this.player_,path, params);
        }
    }

    private static class Listener implements org.bukkit.event.Listener {

        private final BedrockSessionHandler handler_;

        Listener(BedrockSessionHandler handler) {
            this.handler_ = handler;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        void onPlayerMove(PlayerMoveEvent e) {
            Iterator<BedrockSessionHandler.Login> i = this.handler_.logins_.iterator();
            while (i.hasNext()) {
                BedrockSessionHandler.Login l = i.next();
                if (l.player_.equals(e.getPlayer())) {
                    l.sendForm();
                }
            }
        }
        @EventHandler
        void onPlayerQuit(PlayerQuitEvent e) {
            Iterator<BedrockSessionHandler.Login> i = this.handler_.logins_.iterator();
            while (i.hasNext()) {
                BedrockSessionHandler.Login l = i.next();
                if (l.player_.equals(e.getPlayer())) { i.remove(); }
            }
        }
    }
}