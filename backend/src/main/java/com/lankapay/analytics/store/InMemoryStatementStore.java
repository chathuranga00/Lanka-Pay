package com.lankapay.analytics.store;

import com.lankapay.analytics.domain.StatementExportAudit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStatementStore {

    private final ConcurrentHashMap<String, StatementExportAudit> storage = new ConcurrentHashMap<>();

    public StatementExportAudit save(StatementExportAudit audit) {
        storage.put(audit.token(), audit);
        return audit;
    }

    public Optional<StatementExportAudit> findByToken(String token) {
        StatementExportAudit audit = storage.get(token);
        if (audit != null && audit.isExpired()) {
            storage.remove(token);
            return Optional.empty();
        }
        return Optional.ofNullable(audit);
    }
}
