package com.kntrel.mc.accwarden.view;

import com.kntrel.mc.accwarden.form.FormElement;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface View<T> {

    ViewBody<T> body(Set<String> failedValidations);

    default ViewBody<T> body() {
        return this.body(Set.of());
    }

    default CompletableFuture<T> present(Player player, Platform platform) {
        return ViewPresenter.present(player, platform, this);
    }

    static <T> ViewBuilder.Body<T> create() {
        return ViewBuilder.create();
    }

    static <T> View<T> of(Iterable<? extends FormElement> elements, Iterable<? extends ViewAction<? extends T>> actions) {
        ViewBody<T> body = new ViewBody<>(elements, actions);
        return _ -> body;
    }

    static <T> View<T> of(List<? extends FormElement> elements, List<? extends ViewAction<? extends T>> actions) {
        return View.of((Iterable<? extends FormElement>) elements, actions);
    }
}
