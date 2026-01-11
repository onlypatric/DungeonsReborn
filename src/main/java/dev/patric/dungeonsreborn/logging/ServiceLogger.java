package dev.patric.dungeonsreborn.logging;

import java.util.Objects;

public final class ServiceLogger {
  private final ServiceLogManager manager;
  private final ServiceLogCategory category;

  ServiceLogger(ServiceLogManager manager, ServiceLogCategory category) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.category = Objects.requireNonNull(category, "category");
  }

  public void debug(String message) {
    manager.log(category, ServiceLogLevel.DEBUG, message, null);
  }

  public void debug(String message, Throwable throwable) {
    manager.log(category, ServiceLogLevel.DEBUG, message, throwable);
  }

  public void info(String message) {
    manager.log(category, ServiceLogLevel.INFO, message, null);
  }

  public void warn(String message) {
    manager.log(category, ServiceLogLevel.WARNING, message, null);
  }

  public void warn(String message, Throwable throwable) {
    manager.log(category, ServiceLogLevel.WARNING, message, throwable);
  }

  public void error(String message) {
    manager.log(category, ServiceLogLevel.ERROR, message, null);
  }

  public void error(String message, Throwable throwable) {
    manager.log(category, ServiceLogLevel.ERROR, message, throwable);
  }
}
