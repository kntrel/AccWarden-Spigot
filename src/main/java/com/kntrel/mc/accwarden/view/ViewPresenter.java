package com.kntrel.mc.accwarden.view;

import com.kntrel.mc.accwarden.form.Form;
import com.kntrel.mc.accwarden.form.FormAction;
import com.kntrel.mc.accwarden.form.FormActionRole;
import com.kntrel.mc.accwarden.form.FormResponse;
import com.kntrel.mc.accwarden.form.FormResponseKind;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class ViewPresenter {

    private ViewPresenter() {}

    static <T> CompletableFuture<T> present(Player player, Platform platform, View<T> view) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(view, "view");

        CompletableFuture<T> future = new CompletableFuture<>();
        render_(player, platform, view, Set.of(), future);
        return future;
    }

    private static <T> void render_(
            Player player,
            Platform platform,
            View<T> view,
            Set<String> failedValidations,
            CompletableFuture<T> future
    ) {
        Objects.requireNonNull(failedValidations, "failedValidations");
        if (future.isDone()) {
            return;
        }

        try {
            ViewBody<T> body = view.body(failedValidations);
            Map<String, ViewAction<? extends T>> actions = actionsById_(body);
            platform.formRenderer().show(
                    player,
                    form_(view, body),
                    response -> onResponse_(player, platform, view, body, actions, response, future)
            );
        }
        catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
    }

    private static <T> Form form_(View<T> view, ViewBody<T> body) {
        return new Form(
                formId_(view),
                "",
                body.elements(),
                formActions_(body),
                body.allowExit()
        );
    }

    private static <T> Map<String, ViewAction<? extends T>> actionsById_(ViewBody<T> body) {
        return body.actions()
                .stream()
                .collect(Collectors.toMap(
                        ViewAction::id,
                        action -> action,
                        (_, replacement) -> replacement,
                        LinkedHashMap::new
                ));
    }

    private static <T> String formId_(View<T> view) {
        return "view_" + Integer.toHexString(System.identityHashCode(view));
    }

    private static <T> List<FormAction> formActions_(ViewBody<T> body) {
        List<FormAction> actions = new ArrayList<>();
        boolean primary = true;
        for (ViewAction<? extends T> action : body.actions()) {
            actions.add(new FormAction(
                    action.id(),
                    action.label(),
                    primary ? FormActionRole.PRIMARY : FormActionRole.SECONDARY
            ));
            primary = false;
        }
        return actions;
    }

    private static <T> void onResponse_(
            Player player,
            Platform platform,
            View<T> view,
            ViewBody<T> body,
            Map<String, ViewAction<? extends T>> actions,
            FormResponse response,
            CompletableFuture<T> future
    ) {
        if (future.isDone()) {
            return;
        }

        try {
            if (response.kind().equals(FormResponseKind.CLOSED)) {
                Optional<ViewAction<? extends T>> exitAction = body.allowExit()
                        ? body.exitAction()
                        : Optional.empty();
                if (exitAction.isEmpty()) {
                    future.cancel(false);
                    return;
                }
                completeAction_(player, platform, view, exitAction.get(), new ViewResult(Map.of()), future);
                return;
            }

            if (!response.kind().equals(FormResponseKind.ACTION)) {
                future.cancel(false);
                return;
            }

            String actionId = response.actionId().orElseThrow();
            ViewAction<? extends T> action = actions.get(actionId);
            if (action == null) {
                future.completeExceptionally(new IllegalArgumentException("Unknown view action: " + actionId));
                return;
            }

            ViewResult result = new ViewResult(response.values());
            completeAction_(player, platform, view, action, result, future);
        }
        catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
    }

    private static <T> void completeAction_(
            Player player,
            Platform platform,
            View<T> view,
            ViewAction<? extends T> action,
            ViewResult result,
            CompletableFuture<T> future
    ) {
        Set<String> failedValidations = failedValidations_(action, result);
        if (!failedValidations.isEmpty()) {
            render_(player, platform, view, failedValidations, future);
            return;
        }

        if (action instanceof ViewAction.Return<? extends T> returnAction) {
            future.complete(returnAction.getValue(result));
            return;
        }
        if (action instanceof ViewAction.Link<? extends T> linkAction) {
            forward_(linkAction.getSubView(result).present(player, platform), future);
            return;
        }

        future.completeExceptionally(new IllegalStateException("Unsupported view action: " + action.getClass().getName()));
    }

    private static Set<String> failedValidations_(ViewAction<?> action, ViewResult result) {
        return action.validations()
                .stream()
                .filter(validation -> !validation.test(result))
                .map(Validation::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static <T, U extends T> void forward_(CompletableFuture<U> source, CompletableFuture<T> target) {
        source.whenComplete((value, throwable) -> {
            if (throwable != null) {
                target.completeExceptionally(throwable);
                return;
            }
            target.complete(value);
        });
    }
}
