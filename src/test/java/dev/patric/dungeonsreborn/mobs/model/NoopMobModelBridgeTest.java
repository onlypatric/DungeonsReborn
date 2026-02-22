package dev.patric.dungeonsreborn.mobs.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class NoopMobModelBridgeTest {
  @Test
  void isUnavailableAndSafeToCall() {
    NoopMobModelBridge bridge = new NoopMobModelBridge();
    assertFalse(bridge.available());
    assertFalse(bridge.attach(null, null));
    bridge.update(null, null);
    bridge.play(null, "idle");
    bridge.detach(null);
    assertNotNull(bridge.providerKey());
  }
}
