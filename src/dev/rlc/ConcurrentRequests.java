package dev.rlc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConcurrentRequests {
    public static final Path orderTracking = Path.of("receipts.json");
    public static void main(String[] args) {
        Map<String,Integer> orderMap =
                Map.of("apples", 500,
                        "oranges", 1000,
                        "bananas", 750,
                        "carrots", 2000,
                        "cantaloupes", 100);

        String urlparams = "product=%s&amount=%d";
        String urlBase = "http://localhost:8080";

        List<URI> sites = new ArrayList<>();
        orderMap.forEach( (k,v) -> sites.add(URI.create(
                urlBase + "?" + urlparams.formatted(k,v)
        )));

        HttpClient client = HttpClient.newHttpClient();
//        sendGets(client, sites);

        if(!Files.exists(orderTracking)) {
            try {
                Files.createFile(orderTracking);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        sendPostsWithFileResponse(client, urlBase, orderMap, urlparams);
    }

    private static void sendGets(HttpClient client, List<URI> uris) {
        var futures = uris.stream()
                .map(HttpRequest::newBuilder)
                .map(HttpRequest.Builder::build)
                .map(httpRequest -> client.sendAsync(
                        httpRequest, HttpResponse.BodyHandlers.ofString()
                )).toList();

        var allFutureRequests = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture<?>[0])
        );

        allFutureRequests.join();

        futures.forEach(f -> System.out.println(f.join().body()));
    }

    //challenge
    private static void sendPosts(HttpClient client, String urlBase
            , Map<String,Integer> orders, String formattable) {
        var futures = orders.entrySet()
                .stream()
                .map((k)->{
                    HttpRequest request = HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    formattable.formatted(k.getKey(), k.getValue())
                            ))
                            .uri(URI.create(urlBase))
                            .header("Content-Type",
                                    "application/x-www-form-urlencoded")
                            .build();
                    return request;
                })
                .map(request -> client.sendAsync(request, HttpResponse.BodyHandlers
                        .ofString()))
                .toList();
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        allFutures.join();
        List<String> lines = new ArrayList<>();

        futures.forEach(f->lines.add(f.join().body()));

        try {
            Files.write(orderTracking, lines, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendPostsWithFileResponse(HttpClient client, String urlBase
            , Map<String,Integer> orders, String formattable) {
        var futures = orders.entrySet()
                .stream()
                .map((k)->{
                    HttpRequest request = HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    formattable.formatted(k.getKey(), k.getValue())
                            ))
                            .uri(URI.create(urlBase))
                            .header("Content-Type",
                                    "application/x-www-form-urlencoded")
                            .build();
                    return request;
                })
                .map(request -> client.sendAsync(request, HttpResponse.BodyHandlers
                        .ofFile(orderTracking, StandardOpenOption.APPEND)))
                .toList();
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        allFutures.join();
    }
}
