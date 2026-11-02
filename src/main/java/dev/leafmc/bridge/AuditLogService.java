package dev.leafmc.bridge;

import java.time.Instant;
import java.util.logging.Logger;

public final class AuditLogService {
    private final Logger log;

    public AuditLogService(Logger log) {
        this.log = log;
    }

    public void record(String user, String id, String action, String args, String result) {
        AuditRecord r = AuditRecord.of(id, user, action, args, result);
        log.info(() -> "[AUDIT] ts=" + r.timestamp() + " discord=" + r.discordUsername() + "(" + r.discordUserId()
                + ") action=" + r.action() + " args=" + r.arguments() + " result=" + r.result());
    }
}
