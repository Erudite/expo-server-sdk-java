package io.github.jav.exposerversdk.helpers;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public interface PushServerResolver {
	public CompletableFuture<String> postAsync(URL url, String json, Map<String, String> headers) throws CompletionException;
}
