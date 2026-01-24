package dev.patric.dungeonsreborn.effects.config;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public final class YamlErrors {
  private YamlErrors() {
  }

  public static String missingKeyMessage(java.util.Map<String, Object> node, String key, String path) {
    StringBuilder msg = new StringBuilder(path).append(": missing ").append(key);
    String suggestion = suggestClosest(key, node.keySet());
    if (suggestion != null) {
      msg.append(" (did you mean ").append(suggestion).append("?)");
    } else if (!node.isEmpty()) {
      msg.append(" (available: ").append(formatKeys(node.keySet(), 8)).append(")");
    }
    return msg.toString();
  }

  public static String formatKeys(Set<String> keys, int limit) {
    if (keys.isEmpty()) {
      return "";
    }
    int count = 0;
    StringBuilder out = new StringBuilder();
    for (String k : keys) {
      if (count++ >= limit) {
        out.append(", ...");
        break;
      }
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(k);
    }
    return out.toString();
  }

  public static <E extends Enum<E>> String suggestEnumValue(String raw, Class<E> enumType) {
    if (raw == null) {
      return null;
    }
    java.util.List<String> options = new java.util.ArrayList<>();
    for (E e : enumType.getEnumConstants()) {
      options.add(e.name());
    }
    return suggestClosest(raw, options);
  }

  public static String suggestClosest(String input, Collection<String> options) {
    String in = normalizeToken(input);
    if (in.isEmpty() || options.isEmpty()) {
      return null;
    }
    String best = null;
    int bestDist = Integer.MAX_VALUE;
    for (String opt : options) {
      if (opt == null) {
        continue;
      }
      String norm = normalizeToken(opt);
      if (norm.isEmpty()) {
        continue;
      }
      int dist = editDistance(in, norm);
      if (norm.startsWith(in) || in.startsWith(norm) || norm.contains(in)) {
        dist = Math.min(dist, 1);
      }
      if (dist < bestDist) {
        bestDist = dist;
        best = opt;
      }
    }
    int maxDist = Math.max(1, Math.min(4, in.length() / 3 + 1));
    return bestDist <= maxDist ? best : null;
  }

  public static String normalizeToken(String raw) {
    if (raw == null) {
      return "";
    }
    String s = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        out.append(c);
      }
    }
    return out.toString();
  }

  public static int editDistance(String a, String b) {
    if (a.equals(b)) {
      return 0;
    }
    int alen = a.length();
    int blen = b.length();
    if (alen == 0) {
      return blen;
    }
    if (blen == 0) {
      return alen;
    }
    int[] prev = new int[blen + 1];
    int[] curr = new int[blen + 1];
    for (int j = 0; j <= blen; j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= alen; i++) {
      curr[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= blen; j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        int insert = curr[j - 1] + 1;
        int delete = prev[j] + 1;
        int replace = prev[j - 1] + cost;
        curr[j] = Math.min(insert, Math.min(delete, replace));
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[blen];
  }
}
