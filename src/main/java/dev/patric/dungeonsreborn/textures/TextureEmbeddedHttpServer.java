package dev.patric.dungeonsreborn.textures;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class TextureEmbeddedHttpServer {
  private static final String CONTENT_TYPE_ZIP = "application/zip";
  private static final String ETAG_HEADER = "ETag";
  private static final String IF_NONE_MATCH = "If-None-Match";
  private static final String CACHE_CONTROL = "Cache-Control";

  private final Logger logger;
  private HttpServer server;
  private String routePath = "/dungeonsreborn/generated-pack.zip";
  private String bindHost = "0.0.0.0";
  private int boundPort = 0;
  private volatile File packFile;
  private volatile String packSha1;

  public TextureEmbeddedHttpServer(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public synchronized void start(String host, int port, String path) throws IOException {
    stop();
    bindHost = normalizeHost(host, "0.0.0.0");
    int safePort = sanitizeBindPort(port, 0);
    routePath = normalizePath(path);
    InetSocketAddress address = new InetSocketAddress(bindHost, safePort);
    server = HttpServer.create(address, 0);
    server.createContext(routePath, new PackHandler());
    server.setExecutor(null);
    server.start();
    InetSocketAddress actual = server.getAddress();
    boundPort = actual == null ? safePort : actual.getPort();
    logger.info("[Textures] Embedded delivery server started at " + bindHost + ":" + boundPort + routePath);
  }

  public synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
      logger.info("[Textures] Embedded delivery server stopped");
    }
    boundPort = 0;
  }

  public synchronized boolean isRunning() {
    return server != null;
  }

  public synchronized void setPack(File file, String sha1) {
    packFile = file;
    packSha1 = normalizeSha1(sha1);
  }

  public synchronized String publicUrl(String scheme, String publicHost, int publicPort, String fallbackHost) {
    if (!isRunning()) {
      return "";
    }
    String safeScheme = normalizeScheme(scheme);
    String host = normalizeHost(publicHost, "");
    if (host.isBlank()) {
      host = normalizeHost(fallbackHost, "");
    }
    if (host.isBlank()) {
      host = isWildcard(bindHost) ? "127.0.0.1" : bindHost;
    }
    int port = sanitizePublicPort(publicPort, boundPort);
    try {
      URI uri = new URI(safeScheme, null, host, port, routePath, null, null);
      return uri.toASCIIString();
    } catch (Exception ignored) {
      return safeScheme + "://" + host + ":" + port + routePath;
    }
  }

  private final class PackHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
        return;
      }
      File file = packFile;
      if (file == null || !file.exists() || !file.isFile()) {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
        return;
      }
      String etag = packSha1;
      if (!etag.isBlank()) {
        String ifNoneMatch = exchange.getRequestHeaders().getFirst(IF_NONE_MATCH);
        if (ifNoneMatch != null && stripQuotes(ifNoneMatch).equalsIgnoreCase(etag)) {
          exchange.getResponseHeaders().set(ETAG_HEADER, quote(etag));
          exchange.sendResponseHeaders(304, -1);
          exchange.close();
          return;
        }
      }
      exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_ZIP);
      exchange.getResponseHeaders().set(CACHE_CONTROL, "no-cache");
      if (!etag.isBlank()) {
        exchange.getResponseHeaders().set(ETAG_HEADER, quote(etag));
      }
      long length = file.length();
      if ("HEAD".equalsIgnoreCase(method)) {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
        return;
      }
      exchange.sendResponseHeaders(200, length);
      try (InputStream in = Files.newInputStream(file.toPath());
          OutputStream out = exchange.getResponseBody()) {
        in.transferTo(out);
      } finally {
        exchange.close();
      }
    }
  }

  private static String normalizePath(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isBlank()) {
      return "/dungeonsreborn/generated-pack.zip";
    }
    if (!value.startsWith("/")) {
      value = "/" + value;
    }
    return value.replaceAll("/{2,}", "/");
  }

  private static String normalizeHost(String raw, String fallback) {
    String value = raw == null ? "" : raw.trim();
    return value.isBlank() ? fallback : value;
  }

  private static int sanitizeBindPort(int raw, int fallback) {
    if (raw >= 1 && raw <= 65535) {
      return raw;
    }
    if (raw == 0) {
      return 0;
    }
    return fallback;
  }

  private static int sanitizePublicPort(int raw, int fallback) {
    if (raw >= 1 && raw <= 65535) {
      return raw;
    }
    return fallback;
  }

  private static String normalizeScheme(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if ("https".equals(value)) {
      return "https";
    }
    return "http";
  }

  private static boolean isWildcard(String host) {
    if (host == null) {
      return true;
    }
    String value = host.trim();
    return value.isBlank() || "0.0.0.0".equals(value) || "::".equals(value);
  }

  private static String stripQuotes(String raw) {
    if (raw == null) {
      return "";
    }
    String value = raw.trim();
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static String quote(String raw) {
    return "\"" + raw + "\"";
  }

  private static String normalizeSha1(String raw) {
    if (raw == null) {
      return "";
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (!value.matches("[0-9a-f]{40}")) {
      return "";
    }
    return value;
  }
}
