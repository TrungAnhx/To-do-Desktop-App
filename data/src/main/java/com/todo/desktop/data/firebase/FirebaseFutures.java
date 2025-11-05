package com.todo.desktop.data.firebase;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.CompletableFuture;

public final class FirebaseFutures {

    private FirebaseFutures() {
    }

    public static <T> CompletableFuture<T> toCompletable(ApiFuture<T> future) {
        CompletableFuture<T> result = new CompletableFuture<>();
        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onSuccess(T value) {
                result.complete(value);
            }
        }, MoreExecutors.directExecutor());
        return result;
    }
}
