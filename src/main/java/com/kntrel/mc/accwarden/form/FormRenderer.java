package com.kntrel.mc.accwarden.form;

import org.bukkit.entity.Player;

public interface FormRenderer {

    FormHandle show(Player player, Form form, FormCallback handler);
}
