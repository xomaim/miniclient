package com.minicraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Small blocking HTTP helpers. Always call these off the render thread. */
public final class MiniHttp {
	public static final String UA = "xomaim/miniclient (Mini Client in-game)";
	private static final HttpClient C = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();

	public static HttpRequest.Builder req(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", UA);
	}

	public static HttpResponse<String> send(HttpRequest r) throws Exception {
		return C.send(r, HttpResponse.BodyHandlers.ofString());
	}

	public static JsonElement json(String url) throws Exception {
		HttpResponse<String> r = send(req(url).GET().build());
		if (r.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + r.statusCode());
		return JsonParser.parseString(r.body());
	}

	public static byte[] bytes(String url) throws Exception {
		HttpResponse<byte[]> r = C.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + r.statusCode());
		return r.body();
	}

	public static void download(String url, Path dest) throws Exception {
		Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
		HttpResponse<Path> r = C.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
		if (r.statusCode() / 100 != 2) { java.nio.file.Files.deleteIfExists(tmp); throw new RuntimeException("HTTP " + r.statusCode()); }
		java.nio.file.Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	public static String enc(String s) {
		return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
	}
}
