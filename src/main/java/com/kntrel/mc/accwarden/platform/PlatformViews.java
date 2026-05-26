package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.session.AuthenticationAction;
import com.kntrel.mc.accwarden.session.AuthenticationViewState;
import com.kntrel.mc.accwarden.session.RegistrationAction;
import com.kntrel.mc.accwarden.session.RegistrationViewState;
import com.kntrel.mc.accwarden.view.View;
import org.bukkit.entity.Player;

public interface PlatformViews {

    View<AuthenticationAction> authentication(Player player, AuthenticationViewState state);

    View<RegistrationAction> registration(Player player, RegistrationViewState state);
}
