package dev.patric.dungeonsreborn.textures;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextureService {
  public record Config(
      boolean enabled,
      String namespace,
      boolean autoBuildOnReload,
      String sourceDir,
      String buildDir,
      String zipName,
      String packDescription,
      int packFormat,
      boolean deliveryEnabled,
      String deliveryUrl,
      String deliverySha1,
      boolean deliveryRequired,
      String deliveryPrompt,
      boolean deliveryEmbeddedEnabled,
      String deliveryEmbeddedBind,
      int deliveryEmbeddedPort,
      String deliveryEmbeddedPublicHost,
      int deliveryEmbeddedPublicPort,
      String deliveryEmbeddedScheme,
      String deliveryEmbeddedPath,
      boolean compatWriteCustomModelData,
      int compatCmdRegistryStart,
      File dataFolder) {

    public File resolvedSourceDir() {
      return resolveDir(dataFolder, sourceDir);
    }

    public File resolvedBuildDir() {
      return resolveDir(dataFolder, buildDir);
    }
  }

  public record TextureStats(
      boolean enabled,
      int discoveredTextures,
      int modelMappings,
      String zipSha1,
      String zipPath,
      String deliveryUrl,
      int warnings,
      int errors) {
  }

  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private final JavaPlugin plugin;
  private Config config;
  private TextureCmdRegistry cmdRegistry;
  private TextureEmbeddedHttpServer embeddedDelivery;
  private TextureBuildResult lastBuild = TextureBuildResult.disabled();

  public TextureService(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    reloadConfig();
  }

  public synchronized void reloadConfig() {
    ConfigurationSection section = plugin.getConfig().getConfigurationSection("assets.textures");
    boolean enabled = section == null || section.getBoolean("enabled", true);
    String namespace = TextureModelRegistry.normalizeNamespace(string(section, "namespace", "dungeonsreborn"));
    boolean autoBuildOnReload = bool(section, "autoBuildOnReload", true);
    String sourceDir = string(section, "sourceDir", "assets/textures");
    String buildDir = string(section, "buildDir", "assets/generated/resourcepack");
    String zipName = string(section, "zipName", "dungeonsreborn-generated-pack.zip");
    String packDescription = string(section, "packDescription", "DungeonsReborn Generated Pack");
    int packFormat = intValue(section, "packFormat", 46);

    ConfigurationSection delivery = section == null ? null : section.getConfigurationSection("delivery");
    boolean deliveryEnabled = bool(delivery, "enabled", false);
    String deliveryUrl = string(delivery, "url", "");
    String deliverySha1 = normalizeSha1(string(delivery, "sha1", ""));
    boolean deliveryRequired = bool(delivery, "required", false);
    String deliveryPrompt = string(delivery, "prompt", "<gold>Custom textures required</gold>");
    ConfigurationSection deliveryEmbedded = delivery == null ? null : delivery.getConfigurationSection("embedded");
    boolean deliveryEmbeddedEnabled = bool(deliveryEmbedded, "enabled", false);
    String deliveryEmbeddedBind = string(deliveryEmbedded, "bind", "0.0.0.0");
    int deliveryEmbeddedPort = intValue(deliveryEmbedded, "port", 0);
    String deliveryEmbeddedPublicHost = string(deliveryEmbedded, "publicHost", "");
    int deliveryEmbeddedPublicPort = intValue(deliveryEmbedded, "publicPort", 0);
    String deliveryEmbeddedScheme = string(deliveryEmbedded, "scheme", "http");
    String deliveryEmbeddedPath = string(deliveryEmbedded, "path", "/dungeonsreborn/generated-pack.zip");

    ConfigurationSection compat = section == null ? null : section.getConfigurationSection("compat");
    boolean compatCmd = bool(compat, "writeCustomModelData", true);
    int cmdStart = intValue(compat, "cmdRegistryStart", 10000);

    this.config = new Config(
        enabled,
        namespace,
        autoBuildOnReload,
        sourceDir,
        buildDir,
        zipName,
        packDescription,
        packFormat,
        deliveryEnabled,
        deliveryUrl,
        deliverySha1,
        deliveryRequired,
        deliveryPrompt,
        deliveryEmbeddedEnabled,
        deliveryEmbeddedBind,
        deliveryEmbeddedPort,
        deliveryEmbeddedPublicHost,
        deliveryEmbeddedPublicPort,
        deliveryEmbeddedScheme,
        deliveryEmbeddedPath,
        compatCmd,
        cmdStart,
        plugin.getDataFolder());

    File cmdFile = new File(config.resolvedBuildDir().getParentFile(), "texture-cmd-registry.yml");
    this.cmdRegistry = new TextureCmdRegistry(cmdFile, config.compatCmdRegistryStart());
    this.cmdRegistry.load();
    ensureBundledDefaultTextures();
    refreshEmbeddedDelivery();
  }

  public synchronized Config config() {
    return config;
  }

  public synchronized TextureBuildResult rebuild() {
    if (config == null || !config.enabled()) {
      lastBuild = TextureBuildResult.disabled();
      return lastBuild;
    }
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    Collection<TextureAssetRef> assets = discoverAssets(warnings, errors).values();
    List<TexturePackBuilder.CustomModel> customModels = List.of();
    TexturePackBuilder builder = new TexturePackBuilder();
    TextureBuildResult result = builder.build(config, assets, customModels);
    List<String> mergedWarnings = new ArrayList<>();
    mergedWarnings.addAll(warnings);
    if (result.warnings() != null) {
      mergedWarnings.addAll(result.warnings());
    }
    List<String> mergedErrors = new ArrayList<>();
    mergedErrors.addAll(errors);
    if (result.errors() != null) {
      mergedErrors.addAll(result.errors());
    }
    lastBuild = new TextureBuildResult(
        result.success(),
        result.texturesDiscovered(),
        result.modelsWritten(),
        result.buildDir(),
        result.zipFile(),
        result.zipSha1(),
        List.copyOf(mergedWarnings),
        List.copyOf(mergedErrors));
    if (embeddedDelivery != null) {
      embeddedDelivery.setPack(lastBuild.zipFile(), lastBuild.zipSha1());
    }
    return lastBuild;
  }

  public synchronized TextureBuildResult rebuildIfAutoEnabled() {
    if (config == null || !config.enabled() || !config.autoBuildOnReload()) {
      return lastBuild;
    }
    return rebuild();
  }

  public synchronized TextureBuildResult lastBuild() {
    return lastBuild;
  }

  public synchronized TextureStats stats() {
    TextureBuildResult result = lastBuild == null ? TextureBuildResult.disabled() : lastBuild;
    String zipPath = result.zipFile() == null ? "" : result.zipFile().getPath();
    return new TextureStats(
        config != null && config.enabled(),
        result.texturesDiscovered(),
        cmdRegistry == null ? 0 : cmdRegistry.size(),
        result.zipSha1(),
        zipPath,
        deliveryUrl(),
        result.warningCount(),
        result.errorCount());
  }

  public synchronized List<String> validate() {
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    discoverAssets(warnings, errors);
    List<String> out = new ArrayList<>();
    out.addAll(warnings);
    out.addAll(errors);
    return out;
  }

  public synchronized TextureAssetRef resolveItemTexture(
      String texturePath,
      String modelKeyOverride,
      String contextPath,
      List<String> errors) {
    return resolveTexture(texturePath, modelKeyOverride, "items", contextPath, errors);
  }

  public synchronized int assignCompatCustomModelData(String namespacedModelKey) {
    if (config == null || !config.compatWriteCustomModelData() || cmdRegistry == null) {
      return -1;
    }
    return cmdRegistry.assign(namespacedModelKey);
  }

  public synchronized String deliveryUrl() {
    if (config == null) {
      return "";
    }
    if (config.deliveryUrl() != null && !config.deliveryUrl().isBlank()) {
      return config.deliveryUrl().trim();
    }
    if (embeddedDelivery == null || !embeddedDelivery.isRunning()) {
      return "";
    }
    return embeddedDelivery.publicUrl(
        config.deliveryEmbeddedScheme(),
        config.deliveryEmbeddedPublicHost(),
        config.deliveryEmbeddedPublicPort(),
        detectFallbackPublicHost());
  }

  public synchronized boolean sendConfiguredPack(Player player) {
    return sendPackInternal(player, true);
  }

  public synchronized boolean sendPack(Player player) {
    return sendPackInternal(player, false);
  }

  public synchronized int sendConfiguredPackToAll() {
    int sent = 0;
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (sendPack(player)) {
        sent++;
      }
    }
    return sent;
  }

  private boolean sendPackInternal(Player player, boolean requireDeliveryEnabled) {
    if (player == null || config == null) {
      return false;
    }
    if (requireDeliveryEnabled && !config.deliveryEnabled()) {
      return false;
    }
    if (shouldSkipEmbeddedGeneratedDelivery()) {
      return false;
    }
    String url = deliveryUrl();
    if (url.isBlank()) {
      return false;
    }
    String hash = config.deliverySha1();
    if ((hash == null || hash.isBlank()) && lastBuild != null && lastBuild.zipSha1() != null) {
      hash = normalizeSha1(lastBuild.zipSha1());
    }
    if (hash != null && hash.isBlank()) {
      hash = null;
    }
    Component prompt = MINI.deserialize(config.deliveryPrompt());
    try {
      player.setResourcePack(url, hash, config.deliveryRequired(), prompt);
      return true;
    } catch (Exception ex) {
      plugin.getLogger().warning("[Textures] Failed to send resource pack to " + player.getName() + ": " + ex.getMessage());
      return false;
    }
  }

  private boolean shouldSkipEmbeddedGeneratedDelivery() {
    if (config == null) {
      return true;
    }
    if (!config.deliveryEmbeddedEnabled()) {
      return false;
    }
    if (config.deliveryUrl() != null && !config.deliveryUrl().isBlank()) {
      return false;
    }
    TextureBuildResult build = lastBuild;
    if (build == null || !build.success()) {
      return true;
    }
    return build.texturesDiscovered() <= 0 && build.modelsWritten() <= 0;
  }

  public synchronized void shutdown() {
    if (embeddedDelivery != null) {
      embeddedDelivery.stop();
      embeddedDelivery = null;
    }
  }

  private TextureAssetRef resolveTexture(
      String texturePath,
      String modelKeyOverride,
      String category,
      String contextPath,
      List<String> errors) {
    if (config == null || !config.enabled()) {
      return null;
    }
    String normalized;
    try {
      normalized = TexturePathNormalizer.normalizeTexturePath(texturePath, category);
    } catch (IllegalArgumentException ex) {
      if (errors != null) {
        errors.add(contextPath + ": " + ex.getMessage());
      }
      return null;
    }
    String pngRelative = TexturePathNormalizer.ensurePngExtension(normalized);
    File source = new File(config.resolvedSourceDir(), pngRelative);
    if (!source.exists() || !source.isFile()) {
      if (errors != null) {
        errors.add(contextPath + ": missing texture file " + source.getPath());
      }
      return null;
    }
    File mcmeta = new File(source.getPath() + ".mcmeta");
    if (!mcmeta.exists() || !mcmeta.isFile()) {
      mcmeta = null;
    }

    TextureModelRegistry.ModelKeyParts modelKey;
    try {
      modelKey = TextureModelRegistry.resolve(config.namespace(), normalized, modelKeyOverride);
    } catch (IllegalArgumentException ex) {
      if (errors != null) {
        errors.add(contextPath + ": " + ex.getMessage());
      }
      return null;
    }
    String namespaced = TextureModelRegistry.namespacedKey(modelKey);
    return new TextureAssetRef(
        category,
        texturePath == null ? "" : texturePath,
        normalized,
        modelKey.path(),
        namespaced,
        source,
        mcmeta);
  }

  private Map<String, TextureAssetRef> discoverAssets(List<String> warnings, List<String> errors) {
    Map<String, TextureAssetRef> out = new LinkedHashMap<>();
    if (config == null || !config.enabled()) {
      return out;
    }
    File sourceRoot = config.resolvedSourceDir();
    File items = new File(sourceRoot, "items");
    if (!items.exists()) {
      items.mkdirs();
    }
    List<File> pngs = new ArrayList<>();
    collectPngFiles(sourceRoot, pngs);
    for (File file : pngs) {
      String relative = sourceRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
      String lower = relative.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("items/")) {
        continue;
      }
      TextureAssetRef ref = resolveTexture(relative, null, "items", "assets.textures", errors);
      if (ref == null) {
        continue;
      }
      TextureAssetRef previous = out.put(ref.namespacedModelKey(), ref);
      if (previous != null) {
        warnings.add("duplicate model key mapped to multiple textures: " + ref.namespacedModelKey());
      }
    }
    return out;
  }

  private static void collectPngFiles(File dir, List<File> out) {
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (child.isDirectory()) {
        collectPngFiles(child, out);
        continue;
      }
      if (child.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
        out.add(child);
      }
    }
  }

  private static String string(ConfigurationSection section, String key, String fallback) {
    if (section == null) {
      return fallback;
    }
    String value = section.getString(key);
    return value == null ? fallback : value;
  }

  private static boolean bool(ConfigurationSection section, String key, boolean fallback) {
    return section == null ? fallback : section.getBoolean(key, fallback);
  }

  private static int intValue(ConfigurationSection section, String key, int fallback) {
    return section == null ? fallback : section.getInt(key, fallback);
  }

  private static String normalizeSha1(String raw) {
    if (raw == null) {
      return "";
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      return "";
    }
    if (!value.matches("[0-9a-f]{40}")) {
      return "";
    }
    return value;
  }

  private void refreshEmbeddedDelivery() {
    if (embeddedDelivery != null) {
      embeddedDelivery.stop();
      embeddedDelivery = null;
    }
    if (config == null || !config.enabled() || !config.deliveryEmbeddedEnabled()) {
      return;
    }
    TextureEmbeddedHttpServer server = new TextureEmbeddedHttpServer(plugin.getLogger());
    try {
      server.start(
          config.deliveryEmbeddedBind(),
          config.deliveryEmbeddedPort(),
          config.deliveryEmbeddedPath());
      server.setPack(lastBuild == null ? null : lastBuild.zipFile(), lastBuild == null ? "" : lastBuild.zipSha1());
      embeddedDelivery = server;
      String resolved = deliveryUrl();
      if (!resolved.isBlank()) {
        plugin.getLogger().info("[Textures] Embedded delivery URL: " + resolved);
      }
    } catch (Exception ex) {
      plugin.getLogger().warning("[Textures] Failed to start embedded delivery server: " + ex.getMessage());
      embeddedDelivery = null;
    }
  }

  private String detectFallbackPublicHost() {
    String ip = plugin.getServer().getIp();
    if (ip != null && !ip.isBlank()) {
      return ip.trim();
    }
    String bind = config == null ? "" : config.deliveryEmbeddedBind();
    if (!isWildcardBind(bind)) {
      return bind;
    }
    return "127.0.0.1";
  }

  private static boolean isWildcardBind(String host) {
    if (host == null) {
      return true;
    }
    String value = host.trim();
    return value.isBlank() || "0.0.0.0".equals(value) || "::".equals(value);
  }

  private static File resolveDir(File dataFolder, String rawPath) {
    String path = rawPath == null ? "" : rawPath.trim();
    if (path.isBlank()) {
      return dataFolder;
    }
    File file = new File(path);
    if (file.isAbsolute()) {
      return file;
    }
    return new File(dataFolder, path);
  }

  private void ensureBundledDefaultTextures() {
    if (config == null || !config.enabled()) {
      return;
    }
    List<String> bundled = listBundledTextureResources("assets/textures");
    if (bundled.isEmpty()) {
      return;
    }
    File sourceDir = config.resolvedSourceDir();
    int copied = 0;
    for (String resourcePath : bundled) {
      String relative = resourcePath.substring("assets/textures/".length());
      File target = new File(sourceDir, relative);
      if (target.exists()) {
        continue;
      }
      if (!copyResourceTo(resourcePath, target)) {
        plugin.getLogger().warning("[Textures] Failed to export bundled texture: " + resourcePath);
        continue;
      }
      copied++;
    }
    if (copied > 0) {
      plugin.getLogger().info("[Textures] Exported " + copied + " bundled texture assets");
    }
  }

  private boolean copyResourceTo(String resourcePath, File target) {
    try (InputStream in = plugin.getResource(resourcePath)) {
      if (in == null) {
        return false;
      }
      File parent = target.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return false;
      }
      Files.copy(in, target.toPath());
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private List<String> listBundledTextureResources(String prefix) {
    try {
      URL url = plugin.getClass().getClassLoader().getResource(prefix);
      if (url == null) {
        return List.of();
      }
      String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
      String protocol = url.getProtocol();
      if ("file".equalsIgnoreCase(protocol)) {
        Path root = Path.of(url.toURI());
        if (!Files.isDirectory(root)) {
          return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.walk(root)) {
          stream.filter(Files::isRegularFile)
              .forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (isTextureAsset(relative)) {
                  names.add(normalizedPrefix + relative);
                }
              });
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
      }
      if ("jar".equalsIgnoreCase(protocol)) {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        try (JarFile jar = connection.getJarFile()) {
          List<String> names = new ArrayList<>();
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(normalizedPrefix)) {
              continue;
            }
            String relative = name.substring(normalizedPrefix.length());
            if (isTextureAsset(relative)) {
              names.add(name);
            }
          }
          names.sort(Comparator.naturalOrder());
          return names;
        }
      }
    } catch (Exception ex) {
      plugin.getLogger().warning("[Textures] Unable to scan bundled textures: " + ex.getMessage());
    }
    return List.of();
  }

  private static boolean isTextureAsset(String relativePath) {
    String lower = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
    if (!lower.startsWith("items/")) {
      return false;
    }
    return lower.endsWith(".png") || lower.endsWith(".png.mcmeta");
  }
}
