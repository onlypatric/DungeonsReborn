package dev.patric.dungeonsreborn.crafting;

import java.util.List;

public final class CraftingHookSpec {
  public static final class Hook {
    private final String scriptFile;
    private final String scriptInline;
    private final List<String> abilities;
    private final boolean deny;
    private final String message;

    public Hook(String scriptFile, String scriptInline, List<String> abilities, boolean deny, String message) {
      this.scriptFile = scriptFile;
      this.scriptInline = scriptInline;
      this.abilities = abilities == null ? List.of() : List.copyOf(abilities);
      this.deny = deny;
      this.message = message;
    }

    public String scriptFile() {
      return scriptFile;
    }

    public String scriptInline() {
      return scriptInline;
    }

    public List<String> abilities() {
      return abilities;
    }

    public boolean deny() {
      return deny;
    }

    public String message() {
      return message;
    }
  }

  private final Hook pre;
  private final Hook post;
  private final Hook preview;

  public CraftingHookSpec(Hook pre, Hook post, Hook preview) {
    this.pre = pre;
    this.post = post;
    this.preview = preview;
  }

  public Hook pre() {
    return pre;
  }

  public Hook post() {
    return post;
  }

  public Hook preview() {
    return preview;
  }
}
