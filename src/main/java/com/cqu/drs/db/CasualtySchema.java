package com.cqu.drs.db;

import java.util.List;

/**
 * The table the casualty registry adds.
 *
 * <h2>Why three columns are sized for ciphertext</h2>
 * {@code name}, {@code contact_number} and {@code notes} hold the encrypted
 * form of those values, never the values themselves. AES-GCM output is longer
 * than its input and is stored base64 encoded with its initialisation vector,
 * so the columns are sized for roughly twice what the plain value would need
 * rather than for the value itself. A column sized for a name would truncate
 * the ciphertext, and a truncated ciphertext does not decrypt: the record
 * would be lost rather than merely shortened.
 *
 * <h2>Why they are not indexed, and what follows from that</h2>
 * There is no index on the name, because an index on ciphertext is useless:
 * the same name encrypts differently every time, by design, so an equality
 * lookup would never match and a sorted index would sort by nothing. The
 * registry is therefore searched by disaster and read in full, which is how
 * the screens use it. Searching by name would need a separate searchable
 * derivation of it, which is a known trade-off recorded in the report rather
 * than a thing quietly missing.
 *
 * <h2>Why age is not encrypted and the rest is</h2>
 * Encrypting everything sounds safer and is not: encrypted columns cannot be
 * aggregated, and the casualty summary counts by triage category and needs an
 * age range to be useful to a hospital. The fields that identify a person are
 * encrypted; the fields that describe the response are not. The division is
 * written down in PROTOCOL.md 7.3 rather than being left to whoever adds the
 * next column.
 */
public final class CasualtySchema implements SchemaModule {

    private static final String CASUALTIES = """
            CREATE TABLE IF NOT EXISTS casualties (
              casualty_id       VARCHAR(40)  NOT NULL,
              reference_number  VARCHAR(12)  NOT NULL,
              name_enc          VARCHAR(400) NULL,
              contact_enc       VARCHAR(400) NULL,
              notes_enc         VARCHAR(2000) NULL,
              age               INT          NULL,
              triage            VARCHAR(12)  NOT NULL,
              status            VARCHAR(20)  NOT NULL,
              location          VARCHAR(200) NULL,
              referred_to       VARCHAR(100) NULL,
              referred_at       DATETIME(6)  NULL,
              registered_by     VARCHAR(20)  NOT NULL,
              registered_at     DATETIME(6)  NOT NULL,
              last_updated_by   VARCHAR(20)  NULL,
              last_updated_at   DATETIME(6)  NULL,
              PRIMARY KEY (casualty_id),
              KEY idx_casualties_report (reference_number),
              KEY idx_casualties_triage (triage),
              CONSTRAINT fk_casualties_report
                FOREIGN KEY (reference_number)
                REFERENCES disaster_reports (reference_number)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT chk_casualties_triage
                CHECK (triage IN ('IMMEDIATE', 'DELAYED', 'MINOR', 'DECEASED')),
              CONSTRAINT chk_casualties_status
                CHECK (status IN ('REGISTERED', 'TREATED_ON_SITE', 'REFERRED',
                                  'DISCHARGED')),
              CONSTRAINT chk_casualties_age
                CHECK (age IS NULL OR (age >= 0 AND age <= 130)),
              CONSTRAINT chk_casualties_referral_recorded
                CHECK (status <> 'REFERRED'
                       OR (referred_to IS NOT NULL AND referred_at IS NOT NULL))
            )""";

    @Override
    public String name() {
        return "casualty registry";
    }

    @Override
    public List<String> tableDefinitions() {
        return List.of(CASUALTIES);
    }
}
