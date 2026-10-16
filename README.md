# DRS-Enhanced

Disaster Response System for Rockhampton Regional Council.
COIT20258 Software Engineering, Assessment 3.

A three tier rebuild of the DRS-Initial prototype: a JavaFX client, a multi
threaded application server, and a MySQL database. It adds resource allocation
with concurrent access, a casualty registry with encryption of personal data,
and a one off migration of the Assessment 2 data.

---

## What you need

| Item | Version |
| --- | --- |
| Java | 21 |
| MySQL | 8 or later |
| Maven | 3.8 or later |
| JavaFX | 21.0.2, fetched by Maven |

MySQL 8 is required rather than preferred. The schema uses `DATETIME(6)`,
CHECK constraints and the `utf8mb4_0900_ai_ci` collation, none of which are
available in MySQL 5.7 or in MariaDB.

---

## Quick start

```
mvn clean install
mvn -q exec:java -Dexec.mainClass=com.cqu.drs.server.ServerMain
mvn -q javafx:run -Ddrs.client.mode=remote
```

Three commands: build, start the server, start a client. The server creates
the database and its tables on first start. Sign in as `coordinator` with the
password `coord123`.

If your MySQL root password is not blank, supply it before the first command
rather than editing any file:

```
set DRS_DB_PASSWORD=yourpassword        (Windows)
export DRS_DB_PASSWORD=yourpassword     (macOS, Linux)
```

---

## The database

The server creates the schema on startup because `drs.db.createSchema=true`.
To create it yourself instead, run the committed script in MySQL Workbench or
at the command line:

```
mysql -u root -p < docs/drs_enhanced_schema.sql
```

Both routes produce the same 14 tables, the same eight departments and the same
three starting accounts, because the script is generated from the same code the
server runs. A test fails if the two ever disagree.

To regenerate the script after changing the schema:

```
mvn -q compile exec:java -Dexec.mainClass=com.cqu.drs.db.SchemaTool
```

---

## Running the client

The client runs in one of two modes.

| Mode | What it does | When to use it |
| --- | --- | --- |
| `local` | Holds its own repositories, as DRS-Initial did. Needs no server | Looking at the interface without starting anything else |
| `remote` | Holds no repositories and asks the server for everything | The three tier arrangement. The server must be running |

The default is `local`, so the application always starts whatever state the
server is in. Set the mode at startup:

```
mvn -q javafx:run -Ddrs.client.mode=remote
```

or change `drs.client.mode` in `src/main/resources/db.properties`.

To see the multi threaded server working, start the server once and then start
two clients in remote mode. Sign both in as coordinators and allocate from the
same resource pool at the same time. One allocation succeeds and the other is
refused with the number of units actually left.

---

## Starting accounts

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin123` | Administrator |
| `coordinator` | `coord123` | Coordinator |
| `fire.crew` | `crew1234` | Responder, Fire and Emergency (DEP-01) |

All three are flagged as requiring a password change, so the first sign in asks
for a new one. They exist so the system can be marked on a fresh database.

Reporting a disaster and tracking a report need no account at all.

---

## Migrating the Assessment 2 data

```
mvn -q exec:java -Dexec.mainClass=com.cqu.drs.migration.MigrationTool
```

It prints what it moved. Run it a second time and it reports that the work was
already done, because the migration is recorded in a ledger rather than guessed
at. It refuses to run against a database that already holds reports lodged
through the new system, and it never writes back to the Assessment 2 files.

An administrator can also run it from the client, which sends `admin.migrate`.

---

## Running the tests

```
mvn test
```

539 test cases. The tests that need a database work in `drs_enhanced_test`,
never the application's own database, so a test run cannot destroy migrated
data.

| Situation | What you should see |
| --- | --- |
| MySQL running, `DRS_DB_PASSWORD` set | 538 pass, 1 aborts, 0 fail |
| MySQL running, root password blank | 539 pass |
| MySQL stopped | 446 pass, 93 abort, 0 fail |

Nothing fails in any of the three. The tests that abort announce why rather
than passing quietly. The single abort in the first row is T108, which checks
that a blank password in `db.properties` is honoured rather than replaced by a
default, and which cannot check that while a password is supplied from outside.
The 93 in the third row each report that no database is reachable and name the
address they tried.

To run one class:

```
mvn test -Dtest=ResourceAllocationConcurrencyTest
```

---

## Configuration

Nothing needs editing to run the system. Every value in
`src/main/resources/db.properties` can be overridden, in this order of
precedence:

1. A JVM system property, for example `-Ddrs.db.password=secret`
2. An environment variable, for example `DRS_DB_PASSWORD=secret`
3. The file itself
4. The default built into `DatabaseConfig`

| Setting | Default | What it does |
| --- | --- | --- |
| `drs.client.mode` | `local` | `local` or `remote` |
| `drs.server.host` | `localhost` | Where the client looks for the server |
| `drs.server.port` | `5050` | The port the server binds |
| `drs.server.threads` | `32` | Connections served at once |
| `drs.db.host` | `localhost` | MySQL host |
| `drs.db.port` | `3306` | MySQL port |
| `drs.db.name` | `drs_enhanced` | Database name |
| `drs.db.user` | `root` | MySQL account |
| `drs.db.password` | blank | Supply from outside the repository |
| `drs.db.createSchema` | `true` | Create the schema on startup if absent |

Use the override rather than editing the file, so a personal password is never
committed.

---

## Encryption key

Casualty names, contact numbers and notes are encrypted at rest with
AES-256-GCM. The key is read from a file outside the repository and is
generated on first use if it is not there.

`drs-encryption.key` must never be committed. It is listed in `.gitignore`.
Committing it would place the key beside the ciphertext, which is the same as
not encrypting the data at all. If the key is lost, existing casualty records
cannot be read, which is the intended behaviour rather than a fault.

---

## Project layout

| Path | Holds |
| --- | --- |
| `src/main/java/com/cqu/drs/model` | The domain classes and enumerations |
| `src/main/java/com/cqu/drs/service` | Business rules, behind ten operation interfaces |
| `src/main/java/com/cqu/drs/repository` | Repository interfaces, with a MySQL and a JSON implementation of each |
| `src/main/java/com/cqu/drs/db` | Configuration, connections, schema modules, script generator |
| `src/main/java/com/cqu/drs/protocol` | Requests, responses, operations, codec, signer |
| `src/main/java/com/cqu/drs/server` | The server, dispatcher, sessions |
| `src/main/java/com/cqu/drs/server/handler` | One handler per operation |
| `src/main/java/com/cqu/drs/client` | `DrsClient` and the remote service proxies |
| `src/main/java/com/cqu/drs/controller` | One controller per screen |
| `src/main/java/com/cqu/drs/security` | Field encryption, key handling, password hashing |
| `src/main/java/com/cqu/drs/migration` | The Assessment 2 migration and its ledger |
| `src/main/resources/fxml` | The twelve Scene Builder views |
| `src/test/java` | 45 test classes |
| `docs` | Protocol, schema script, test plan, diagrams, report |

---

## Documentation

| Document | What it holds |
| --- | --- |
| `docs/PROTOCOL.md` | Every message shape, operation, error code and access rule |
| `docs/TEST-PLAN.md` | Input data, expected result and actual result per case, plus the manual cases |
| `docs/drs_enhanced_schema.sql` | The database script, generated from the code |
| `docs/GROUP-REPORT.md` | The group report |
| `docs/DRS_Enhanced_*.png` | Architecture, use case, class, ERD and sequence diagrams, with their PlantUML sources |
| `TASKS.md` | The task distribution |
| `PROBLEM-LOG.md` | Problems hit during development and how they were resolved |
| `SELECTION.md` | The review of the three prototypes and the choice of base |

---

## If something does not work

**The client says it cannot reach the server.** The client is in `remote` mode
and the server is not running, or is on another port. Start the server, or set
`drs.client.mode=local` to use the application without one.

**Access denied for user 'root'@'localhost'.** The MySQL password is not
reaching the application. Set `DRS_DB_PASSWORD` in the same terminal you run
Maven from, or pass `-Ddrs.db.password=...`.

**Unknown collation utf8mb4_0900_ai_ci.** The database is MySQL 5.7 or
MariaDB. MySQL 8 is required.

**Port 5050 already in use.** Another process holds it. Start the server with
`-Ddrs.server.port=5051` and the client with the same value.

**93 tests abort when you run the suite.** MySQL is not running. The tests that
need it stand aside and say so rather than passing quietly.
