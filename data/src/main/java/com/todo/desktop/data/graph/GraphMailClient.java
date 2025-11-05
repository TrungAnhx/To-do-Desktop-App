package com.todo.desktop.data.graph;

import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.util.Objects;

public final class GraphMailClient {

    private final GraphServiceClient<Request> graphClient;

    public GraphMailClient(GraphServiceClient<Request> graphClient) {
        this.graphClient = Objects.requireNonNull(graphClient, "graphClient");
    }

    public GraphServiceClient<Request> rawClient() {
        return graphClient;
    }
}
