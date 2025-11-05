package com.todo.desktop.data.graph;

import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.graph.authentication.IAuthenticationProvider;

import java.net.URL;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ClientCredentialAuthProvider implements IAuthenticationProvider {

    private final ConfidentialClientApplication application;
    private final Set<String> scopes;

    public ClientCredentialAuthProvider(ConfidentialClientApplication application, Set<String> scopes) {
        this.application = Objects.requireNonNull(application, "application");
        this.scopes = Objects.requireNonNull(scopes, "scopes");
    }

    @Override
    public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
        ClientCredentialParameters parameters = ClientCredentialParameters
                .builder(scopes)
                .build();
        return application.acquireToken(parameters)
                .thenApply(IAuthenticationResult::accessToken);
    }
}
