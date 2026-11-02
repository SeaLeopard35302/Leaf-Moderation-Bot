package dev.leafmc.bridge;

import java.time.Instant;

public record AuditRecord(Instant timestamp, String discordUserId, String discordUsername, String action,
        String arguments, String result) {
    public static AuditRecord of(String id, String user, String action, String args, String result) {
        return new AuditRecord(Instant.now(), id, user, action, args.replaceAll("[\\r\\n]", " "), result);
    }
}
