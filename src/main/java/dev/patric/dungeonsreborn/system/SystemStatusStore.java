package dev.patric.dungeonsreborn.system;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemStatusStore {
  public record Entry(
      String id,
      String label,
      String source,
      String detail,
      long timestampMs,
      List<String> errors) {
    public int errorCount() {
      return errors == null ? 0 : errors.size();
    }
  }

  public record ErrorEntry(
      String id,
      String label,
      String source,
      String message,
      long timestampMs) {
  }

  private static final SystemStatusStore INSTANCE = new SystemStatusStore();

  public static SystemStatusStore get() {
    return INSTANCE;
  }

  private final Map<String, Entry> entries = new LinkedHashMap<>();

  private SystemStatusStore() {
  }

  public synchronized void record(String id, String label, String source, String detail, List<String> errors) {
    if (id == null || id.isBlank()) {
      return;
    }
    long now = System.currentTimeMillis();
    String safeLabel = label == null || label.isBlank() ? id : label;
    String safeSource = source == null ? "" : source;
    String safeDetail = detail == null ? "" : detail;
    List<String> safeErrors = errors == null ? List.of() : List.copyOf(errors);
    entries.put(id, new Entry(id, safeLabel, safeSource, safeDetail, now, safeErrors));
  }

  public synchronized Entry entry(String id) {
    if (id == null) {
      return null;
    }
    return entries.get(id);
  }

  public synchronized List<Entry> entries() {
    return new ArrayList<>(entries.values());
  }

  public synchronized List<ErrorEntry> errors() {
    return errors(null);
  }

  public synchronized List<ErrorEntry> errors(String id) {
    List<ErrorEntry> out = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (id != null && !id.equals(entry.id())) {
        continue;
      }
      List<String> errors = entry.errors();
      if (errors == null || errors.isEmpty()) {
        continue;
      }
      for (String message : errors) {
        if (message == null || message.isBlank()) {
          continue;
        }
        out.add(new ErrorEntry(entry.id(), entry.label(), entry.source(), message, entry.timestampMs()));
      }
    }
    return out;
  }
}
