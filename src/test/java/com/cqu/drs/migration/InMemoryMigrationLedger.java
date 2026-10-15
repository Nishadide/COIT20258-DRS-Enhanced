package com.cqu.drs.migration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A ledger held in a map, so the migration can be tested without a database.
 *
 * It is here rather than in the main sources because nothing the application
 * does should use it: a ledger that forgets everything when the process ends
 * would let a migration run a second time after a restart, which is the one
 * thing the ledger exists to prevent.
 */
final class InMemoryMigrationLedger implements MigrationLedger {

    private final Map<String, String> entries = new HashMap<>();

    @Override
    public boolean hasRun(String item) {
        return entries.containsKey(item);
    }

    @Override
    public Optional<String> detailOf(String item) {
        return Optional.ofNullable(entries.get(item));
    }

    @Override
    public void record(String item, String detail) {
        entries.put(item, detail);
    }
}
