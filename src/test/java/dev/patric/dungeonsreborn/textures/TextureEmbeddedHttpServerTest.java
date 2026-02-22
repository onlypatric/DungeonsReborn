package dev.patric.dungeonsreborn.textures;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureEmbeddedHttpServerTest {
  @Test
  void servesCurrentPack(@TempDir Path tempDir) throws Exception {
    Path zip = tempDir.resolve("generated-pack.zip");
    byte[] payload = new byte[] {1, 2, 3, 4, 5};
    Files.write(zip, payload);

    TextureEmbeddedHttpServer server = new TextureEmbeddedHttpServer(Logger.getLogger("test"));
    server.start("127.0.0.1", 0, "/pack.zip");
    try {
      server.setPack(zip.toFile(), "0123456789abcdef0123456789abcdef01234567");
      String url = server.publicUrl("http", "127.0.0.1", 0, "127.0.0.1");
      assertFalse(url.isBlank());

      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

      assertTrue(response.statusCode() == 200);
      assertArrayEquals(payload, response.body());
    } finally {
      server.stop();
    }
  }
}
