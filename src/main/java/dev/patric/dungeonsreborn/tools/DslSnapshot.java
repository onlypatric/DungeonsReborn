package dev.patric.dungeonsreborn.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless DSL snapshot tool (parses + normalizes script text for stable diffs).
 */
public final class DslSnapshot {
  private DslSnapshot() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("Usage: DslSnapshot <input.es> <output.txt>");
      System.exit(2);
      return;
    }
    Path input = Path.of(args[0]);
    Path output = Path.of(args[1]);
    String source = Files.readString(input, StandardCharsets.UTF_8);
    try {
      DslLint.lint(source, input.toString());
    } catch (IllegalArgumentException ex) {
      System.err.println("DSL snapshot FAILED: " + ex.getMessage());
      System.exit(1);
      return;
    }
    String snapshot = normalize(source);
    Files.writeString(output, snapshot, StandardCharsets.UTF_8);
    System.out.println("DSL snapshot OK: " + output);
  }

  private static String normalize(String source) {
    StringBuilder out = new StringBuilder();
    String[] lines = source.split("\\R", -1);
    for (String line : lines) {
      String cleaned = stripComments(line);
      String trimmed = cleaned.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String normalized = trimmed.replaceAll("\\s+", " ");
      out.append(normalized).append('\n');
    }
    return out.toString();
  }

  private static String stripComments(String line) {
    StringBuilder out = new StringBuilder();
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (escaped) {
        out.append(c);
        escaped = false;
        continue;
      }
      if (c == '\\') {
        if (inString) {
          escaped = true;
        }
        out.append(c);
        continue;
      }
      if (c == '"') {
        inString = !inString;
        out.append(c);
        continue;
      }
      if (c == '#' && !inString) {
        break;
      }
      out.append(c);
    }
    return out.toString();
  }
}
