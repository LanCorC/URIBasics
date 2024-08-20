package dev.rlc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rlc.handlers.JsonBodyHandler;
import dev.rlc.handlers.ThreadSafeFileHandler;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentRequests {
    private static final Lock lock = new ReentrantLock();
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
        sendPostsGetJSON(client, urlBase, orderMap, urlparams);
    }

    public static void writeToFile(String content) {
        lock.lock();
        try {
            Files.writeString(orderTracking, content + "\r",
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            lock.unlock();
        }
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

    private static void sendPostsGetJSON(HttpClient client, String urlBase
            , Map<String,Integer> orders, String formattable) {
        ObjectMapper objectMapper = new ObjectMapper();
        var handler = JsonBodyHandler.create(objectMapper);
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
                .map(request -> client.sendAsync(request, handler))
                .toList();
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        allFutures.join();

        futures.forEach(f-> {
            JsonNode node = f.join().body().get("order");
            System.out.printf("Order Id:%s Expected Delivery: %s %n",
                    node.get("orderId"),
                    node.get("orderDeliveryDate").asText()
                    );
        });
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

    private static void sendPostsSafeFileWrite(HttpClient client, String urlBase
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
                        .ofString()).thenAcceptAsync(r->writeToFile(r.body())))
                .toList();
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        allFutures.join();
    }

    private static void sendPostsFileHandler(HttpClient client, String urlBase
            , Map<String,Integer> orders, String formattable) {
        var handler = new ThreadSafeFileHandler(orderTracking);
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
                .map(request -> client.sendAsync(request, handler))
                .toList();
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        allFutures.join();
    }
}
