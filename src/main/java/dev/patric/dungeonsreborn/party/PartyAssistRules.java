package dev.patric.dungeonsreborn.party;

public record PartyAssistRules(double baseRadius, double scalePerMember, double maxRadius) {
  public PartyAssistRules {
    baseRadius = Math.max(0.0, baseRadius);
    scalePerMember = Math.max(0.0, scalePerMember);
    maxRadius = Math.max(0.0, maxRadius);
  }

  public double radiusForParty(Party party) {
    int size = party == null ? 1 : Math.max(1, party.size());
    double radius = baseRadius * (1.0 + scalePerMember * Math.max(0, size - 1));
    if (maxRadius > 0.0) {
      radius = Math.min(radius, maxRadius);
    }
    return Math.max(0.0, radius);
  }
}
