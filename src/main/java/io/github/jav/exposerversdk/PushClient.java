package io.github.jav.exposerversdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PushClient {
    static public final long PUSH_NOTIFICATION_CHUNK_LIMIT = 100;
    static public final long PUSH_NOTIFICATION_RECEIPT_CHUNK_LIMIT = 300;
    static public final String BASE_URL = "https://exp.host";
    static public final String BASE_API_URL = BASE_URL + "/--/api/v2";

    public CompletableFuture<List<ExpoPushTicket>> sendPushNotificationsAsync(List<ExpoPushMessage> messages) {
        try {
            return _postNotificationAsync(new URL(BASE_API_URL + "/push/send"), messages)
                    .thenApply((String jsonString) -> {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode dataNode = mapper.readTree(jsonString).get("data");
                            List<ExpoPushTicket> retList = new ArrayList<>();
                            for (JsonNode node : dataNode) {
                                retList.add(mapper.convertValue(node, ExpoPushTicket.class));
                            }
                            return retList;
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CompletableFuture<List<ExpoPushReceiept>> getPushNotificationReceiptsAsync(List<String> _ids) {
        try {
            return _postReceiptsAsync(new URL(BASE_API_URL + "/push/getReceipts"), _ids)
                    .thenApply((String jsonString) -> {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode dataNode = mapper.readTree(jsonString).get("data");
                            List<ExpoPushReceiept> retList = new ArrayList<>();
                            Iterator<Map.Entry<String, JsonNode>> it = dataNode.fields();
                            while (it.hasNext()) {
                                Map.Entry<String, JsonNode> field = it.next();
                                String key = field.getKey();
                                JsonNode expoPushRecieptJsonNode = field.getValue();
                                ExpoPushReceiept epr = mapper.treeToValue(expoPushRecieptJsonNode, ExpoPushReceiept.class);

                                epr.id = key;
                                retList.add(epr);
                            }
                            return retList;
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T> CompletableFuture<String> _postNotificationAsync(URL url, List<T> messages) throws URISyntaxException {

        ObjectMapper objectMapper = new ObjectMapper();
        String json = null;

        try {
            json = objectMapper.
                    writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    return body;
                });
    }

    private class JsonReceiptHelper<T> {
        public List<T> ids;

        public JsonReceiptHelper(List<T> _ids) {
            ids = _ids;
        }
    }

    private <T> CompletableFuture<String> _postReceiptsAsync(URL url, List<T> receipts) throws URISyntaxException {

        JsonReceiptHelper<T> jsonReceiptHelper = new PushClient.JsonReceiptHelper<T>(receipts);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = null;

        try {
            json = objectMapper.
                    writeValueAsString(jsonReceiptHelper);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    return body;
                });
    }

    static public boolean isExponentPushToken(String token) {
        String prefixA = "ExponentPushToken[";
        String prefixB = "ExpoPushToken[";
        String postfix = "]";
        String regex = "[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}";

        if (token.matches(regex)) return true;
        if (!token.endsWith(postfix)) return false;
        if (token.startsWith(prefixA)) return true;
        if (token.startsWith(prefixB)) return true;
        return false;
    }

    public static long _getActualMessagesCount(List<ExpoPushMessage> messages) {
        return messages.stream().reduce(0, (acc, cur) -> acc + cur.to.size(), Integer::sum);
    }

    public List<List<String>> chunkPushNotificationReceiptIds(List<String> recieptIds) {
        return _chunkItems(recieptIds, PUSH_NOTIFICATION_RECEIPT_CHUNK_LIMIT);
    }

    public <T> List<List<T>> _chunkItems(List<T> items, long chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        List<T> chunk = new ArrayList<>();
        for (T item : items) {
            chunk.add(item);
            if (chunk.size() >= chunkSize) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
            }
        }

        if (chunk.size() > 0) {
            chunks.add(chunk);
        }
        return chunks;
    }

    public List<List<ExpoPushMessage>> chunkPushNotifications(List<ExpoPushMessage> messages) {
        List<List<ExpoPushMessage>> chunks = new ArrayList<>();
        List<ExpoPushMessage> chunk = new ArrayList<>();

        long chunkMessagesCount = 0;
        for (ExpoPushMessage message : messages) {
            List<String> partialTo = new ArrayList<>();
            for (String recipient : message.to) {
                if (recipient.length() <= 0) continue;
                partialTo.add(recipient);
                chunkMessagesCount++;
                if (chunkMessagesCount >= PUSH_NOTIFICATION_CHUNK_LIMIT) {
                    // Cap this chunk here if it already exceeds PUSH_NOTIFICATION_CHUNK_LIMIT.
                    // Then create a new chunk to continue on the remaining recipients for this message.
                    chunk.add(new ExpoPushMessage(partialTo, message));
                    chunks.add(chunk);
                    chunk = new ArrayList<>();
                    chunkMessagesCount = 0;
                    partialTo = new ArrayList<>();
                }
            }

            if (partialTo.size() > 0) {
                chunk.add(new ExpoPushMessage(partialTo, message));
            }

            if (chunkMessagesCount >= PUSH_NOTIFICATION_CHUNK_LIMIT) {
                // Cap this chunk if it exceeds PUSH_NOTIFICATION_CHUNK_LIMIT.
                // Then create a new chunk to continue on the remaining messages.
                chunks.add(chunk);
                chunk = new ArrayList<>();
                chunkMessagesCount = 0;
            }
        }

        if (chunkMessagesCount > 0) {
            // Add the remaining chunk to the chunks.
            chunks.add(chunk);
        }

        return chunks;
    }
}
