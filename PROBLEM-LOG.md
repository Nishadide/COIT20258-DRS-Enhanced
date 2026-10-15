# Problem log

Problems hit during development and how they were resolved. Appended as they
happen, not reconstructed afterwards.

Each member's individual teamwork report (Part 2) requires two technical
problems and two collaborative problems with their resolutions. This file is
where those come from, so record both kinds.

Format: one entry per problem, newest at the bottom.

---

### [Date] Technical: [one line saying what broke]

**Who hit it:**
**What happened:**
**What was tried:**
**What fixed it:**
**GitHub issue:** #

---

### [Date] Collaborative: [one line saying what the friction was]

**Who was involved:**
**What happened:**
**How it was negotiated:**
**What was agreed:**

---

### 2026-10-15 Technical: MySQL refused the users table, and reading the SQL did not show why

**Who hit it:** Nishadi, writing the programmatic schema creation.

**What happened:** The schema initialiser created the first three tables and
then failed on the fourth with

```
Function or expression 'department_id' cannot be used in the CHECK clause of
`chk_users_responder_has_department`
```

The message names `department_id` as though it were a function, but it is a
column declared ten lines above the constraint in the same statement. The
statement was printed exactly as it had been sent and inspected by eye, and
nothing was wrong with it.

**What was tried:** The statement was first run through the command line client
rather than through JDBC, to find out whether the driver was altering it. The
apparent result was that the client accepted it, which pointed at the driver
and wasted time, because the test was unsound: the shell check treated any
output as failure and the client echoes every statement it reads from a file,
so a real failure and a successful run looked the same. Re-testing by asking
`information_schema` whether the table now existed showed that the client had
failed too, and that two earlier results recorded as passes had also been
false. The statement was then narrowed one clause at a time, with the existence
of the table checked after each attempt.

Narrowing found the cause. A check constraint that refers to a column covered
by a foreign key is refused when that foreign key can itself change the
column's value, which `ON UPDATE CASCADE` and `ON DELETE SET NULL` can. The
engine will not accept a constraint it would be unable to re-evaluate after a
referential action. `ON UPDATE RESTRICT` and `ON DELETE CASCADE` are both
accepted, the second because deleting the parent removes the child row rather
than leaving one whose constraint would need testing again. The `users` table
needed the refused combination, because
`chk_users_responder_has_department` refers to `department_id`, which is also
the foreign key to `departments`.

**What fixed it:** Every foreign key in the schema now declares
`ON UPDATE RESTRICT`. Nothing is lost: no primary key in this schema is ever
updated, since they are generated identifiers, department codes fixed at
seeding and report references of the form DR-0001, so the cascade could never
have fired. `RESTRICT` additionally refuses an attempt to change an identifier
rather than quietly rewriting every row that refers to it.

Two things were added so that the fault cannot return silently. Test T119
asserts that no definition contains `ON UPDATE CASCADE` and explains why, so
anyone who adds it back is told the reason rather than discovering it again.
The whole schema was then executed against a running server, twice, and the
constraints were tested by attempting fifteen inserts that should be accepted
or rejected. Twelve invalid rows were refused, each by the constraint intended
to refuse it, and the second run of the initialiser reported that it changed
nothing.

**What it changed about how the work is done:** Two lessons, and the second
matters more than the first.

The schema is now executed before it is committed, not read and assumed
correct. Eleven `CREATE TABLE` statements that have never been run are eleven
statements of unknown quality, and this one was rejected for a reason no amount
of reading would have revealed.

A test that cannot fail proves nothing, and is worse than no test, because it
is believed. The first check here reported a pass for a statement that had not
run, and that false pass sent the search in the wrong direction. A test of
whether something worked now asks the system what state it is in, rather than
trusting a command's output or its exit code.

**GitHub issue:** #

---

### 2026-10-15 Technical: replaying a request erased the record of the original

**Who hit it:** Nishadi, building the multi-threaded server.

**What happened:** The server refuses a request whose identifier has been seen
before, which is how a captured request is stopped from being sent twice. The
unit tests for this passed. Run against a real database, the refusal worked and
something else did not.

The same request was sent twice on one connection. The first succeeded, the
second was refused as a replay, and both results were correct as far as the
client could see. The request log told a different story: the row for the
original request now read `ERROR / REPLAY_DETECTED`. The record that it had
succeeded was gone.

The cause was an ordering mistake in the dispatcher. Every request ended by
writing its outcome to the log row bearing its identifier. A replay carries the
identifier of the request it is copying, so writing its outcome overwrote the
original request's row.

The consequence was the opposite of what the log exists for. The request log is
there so that an action cannot later be denied. Anyone who captured a
successful request could have destroyed the evidence that it ever succeeded
simply by sending it again, and the second send needed no key, no session and
no privileges.

**What was tried:** Nothing, in the sense of a search. The fault was visible in
the first line of output as soon as the log was printed, and the cause was
clear once the question was asked. The difficulty was not diagnosis. It was
that nothing in the unit tests had asked the question at all: they checked that
the client received the right refusal and that the handler did not run a second
time, and never checked what the refusal did to the stored record.

**What fixed it:** The outcome is now written only by a request that
successfully claimed its identifier. A replay claims nothing, so it writes
nothing and the original row is untouched. The same applies when the log cannot
be written at all: nothing was recorded, so there is nothing to complete.

A regression test asserts that after a replay the original row still says `OK`
and carries no error code, and that exactly one completion was written for the
two sends.

**What it changed about how the work is done:** A test that checks the reply is
not a test of the side effect. Both defects recorded in this log were found by
running the system against a real database, and neither would have been found
by reading the code or by the tests as they stood. For anything that writes a
record, there is now a test that reads the record back rather than trusting
that the caller was answered correctly.

**GitHub issue:** #

---

### 2026-10-15 Technical: one save method could not tell a new report from a changed one

**Who hit it:** Nishadi, writing the MySQL data access for disaster reports.

**What happened:** A test written to prove that two reports cannot share a
reference failed, and failed in the worst way: nothing was thrown, and the
second report quietly replaced the first.

The repository inherited from Assessment 2 offers one method, `save`, used both
for a report being lodged for the first time and for one that has been assessed
or dispatched. It decided what to do by asking whether the reference was
already stored, and updating if it was.

In Assessment 2 that was sound. One person at one keyboard generated one
reference at a time, so a reference was never claimed twice and save-or-update
was always the right reading.

With a server handling several clients at once it is not sound at all.
References are produced by counting the reports already stored and adding one,
so two people lodging a report in the same moment can both arrive at DR-0005.
The first is stored. The second is seen as a change to the first and overwrites
it. A reported disaster disappears, and nothing anywhere records that it ever
existed: no error, no audit entry, no failed request.

**What was tried:** The first attempt was to detect the clash at the point of
writing, by checking whether the reference existed and letting the insert fail
if it had been taken in the meantime. That is what the failing test exposed as
useless. A legitimate update to an existing report and a second report claiming
a taken reference are indistinguishable at that point, because both are a write
to a reference that already exists. No amount of checking inside one method can
separate two intentions the caller never expressed.

**What fixed it:** The repository now offers `insert` as well as `save`, and
the difference between them is the caller stating which it means. `insert` is
for a report being lodged and refuses a reference that is taken.
`save` is for a report that already exists and has changed. `ReportService`
submits new reports with `insert`.

Both implementations enforce it. The MySQL one does not check first and then
insert, because another client could claim the reference between the question
and the answer, and that window is the one being closed. It attempts the insert
and lets the primary key decide, at the moment of writing, which is the only
moment at which the answer cannot go stale.

A test now stores two different disasters under one reference and asserts that
the second is refused, that the first is untouched, and that only one report
exists afterwards. A second test confirms `save` still updates the report that
`insert` refuses to duplicate, so the two have not collapsed back into one.

**What it changed about how the work is done:** Code inherited from Assessment
2 is not automatically correct in Assessment 3, even when it is unchanged and
its tests still pass. `save` was right for a single-user desktop prototype and
became a data-loss defect the moment a server served two clients at once,
without a line of it being edited. The assumptions a piece of code was written
under travel with it, and moving it into a new architecture is the point at
which those assumptions have to be re-examined rather than inherited.

**GitHub issue:** #

---

### 2026-10-15 Technical: the protocol locked the public out of checking their own report

**Who hit it:** Nishadi, starting the client for the walking skeleton.

**What happened:** Version 1.0 of PROTOCOL.md listed `report.findByReference`
as requiring a session. Writing the client meant reading the welcome screen,
which offers "Check a report" alongside "Report a disaster", with no sign-in
between. The case study is the reason: a person who reports a disaster and
leaves no details has nothing afterwards but the reference number.

Had the protocol been implemented as written, a member of the public would have
been able to report a disaster and then been refused when they tried to find
out what had happened to it. Nothing would have appeared broken. The server
would have answered correctly according to its specification, and the
specification was wrong.

**What was tried:** Nothing to diagnose. The fault was in deciding which of the
two documents was right, and the case study settles that: the capability is
required, so the protocol is what has to change.

**What fixed it:** `report.findByReference` is now public, in the enum, in the
document, and in the test that asserts exactly which operations are public.
PROTOCOL.md is at version 1.1 with the change and the reason recorded, so
anyone reading it later sees that the earlier reading was considered and
rejected rather than overlooked.

Being public, the operation returns whatever report the reference names, and a
reference of the form DR-0001 is guessable. That is acceptable for what a
report carries and would not be for anything more sensitive, so the limitation
and its remedy, a token issued to the reporter when the report is lodged, are
written down rather than left to be discovered by whoever reads the code next.

**What it changed about how the work is done:** The protocol was frozen before
any client was written, and the gate did its job here in a way worth noticing.
The error was found by one person reading the document against the application
it described, days before two other members built clients against it. Had it
been found after their screens were written, the fix would have been a protocol
change invalidating work already done by people who were not in the room when
it was agreed.

The specification is not evidence. It is a claim about what the system should
do, and it is checked against the case study and the existing application
rather than assumed correct because it was written down first.

**GitHub issue:** #

---

### 2026-10-15 Technical: the tamper-evident audit trail never verified at all

**Who hit it:** Nishadi, building the MySQL audit repository.

**What happened:** The audit trail is hash chained so that altering it can be
detected, which is how the non-repudiation requirement in section 2.5 is met.
Entries were written, every row had a sixty-four character hash, each recorded
the hash of the entry before it, and reading the trail back produced exactly
what had been written. By every visible sign it worked.

It did not work. Verifying a trail that nobody had touched reported it broken
at the first entry.

The cause was a mismatch between what was hashed and what was stored. The hash
was taken over the timestamp as Java produced it, to nanosecond precision. The
column is `DATETIME(6)`, which holds microseconds, so the value written was
shorter than the value hashed. Verification read the stored timestamp,
recomputed the hash from it, and got a different answer. Every entry failed,
always, from the first one ever written.

This is the same microsecond truncation already recorded against the disaster
report repository earlier in the day, where it was a cosmetic difference
between a saved object and a reloaded one. Here the same fact made a security
control useless.

**What was tried:** Nothing, because the test said precisely what was wrong.
What matters is which test. The tests written first were the interesting ones:
alter an entry and check the chain notices, delete one and check the gap is
found, change a timestamp and check it is caught. Every one of those passed,
because a chain that never verifies reports tampering whether or not there was
any. Six tests agreeing that tampering is detected proved nothing at all.

The test that found it was the dull one: write three entries, touch nothing,
and check the trail verifies.

**What fixed it:** The timestamp is truncated to microseconds before it is
hashed and before it is stored, so the value hashed and the value kept are the
same value. It is also put back on the entry the caller holds, so what the
caller has matches what was written rather than differing in digits nobody can
see.

**A second fault, found by the same run:** with six threads appending at once,
two failed with `Deadlock found when trying to get lock`. Appends are
serialised by locking the last entry, and InnoDB will roll one transaction back
when two want the same rows. That is not a fault, it is how the database
resolves contention, and the application is expected to try again. A deadlock
and a lock wait timeout are now retried like any other lost race. Treating them
as failures would have meant an audit entry lost whenever two people acted at
the same moment, which is exactly when an audit trail is worth having.

**What it changed about how the work is done:** A detector has to be shown to
be quiet before it is shown to be loud. Testing only that a mechanism catches
the bad case cannot distinguish a working detector from one that fires
constantly, and the second is worse than having none, because it looks like
diligence. Every check of this kind now starts with the case where nothing is
wrong.

**GitHub issue:** 

---

### 2026-10-16 Technical: the protocol was sending every account's password hash

**Who hit it:** Nishadi

**What happened:** While writing the client-side service proxies, the protocol
codec was configured to serialise users with `UserAdapter`, the adapter written
for the JSON files. That adapter writes every field a user has, including
`passwordHash`. The effect was that `auth.login` returned the hash of whoever
signed in, and `account.findAll` returned the hash of every account in the
system in one reply, to any administrator's client and to anything watching the
connection. Hashes are not passwords, but they are what the server compares
against, so a stolen hash is the starting point of an offline guessing attack
that nothing on the server can see or rate limit.

**What was tried:** The fault was not visible by reading the code, because the
registration line looked correct and the adapter is correct for the job it was
written for. It was found by writing a short program that asked the codec what
it actually emitted, for a user serialised three ways: as the declared type
`User`, as its runtime subclass, and inside a list. All three printed the hash.

The first fix attempt was to register the adapter against `User`. That closed
only one of the three: an adapter registered for a type is consulted only when
the declared type matches, and a list of users and an object whose runtime
class is `SystemAdministrator` both fall through to plain reflection.

**What fixed it:** A new `ProtocolUserAdapter`, registered with
`registerTypeHierarchyAdapter` so that it is consulted for `User` and for all
three of its subclasses, which means no handler can get round it by passing an
object without naming its type. It drops any field whose name says it holds a
credential, with booleans exempt so that the `mustChangePassword` flag the
client needs still travels. The JSON stores keep the original adapter, because
a store that dropped the hash could not sign anybody in after a restart.

Eight tests cover it (T252 to T259), and four of them were confirmed to fail
against the old registration before the fix was kept, so they are known to be
capable of catching it.

**GitHub issue:** #

---

### 2026-10-16 Technical: the same refusal arrived as three different failures

**Who hit it:** Nishadi

**What happened:** Each remote service translated the server's refusals into
the exceptions the screens already caught, so that a controller would behave
the same whether it was talking to a local service or to the server. Each
service did its own translating, and they disagreed. A role refusal from
`account.create` arrived as an `IllegalArgumentException`, the same refusal
from `account.findAll` arrived as a raw `ProtocolException`, because that
method had nothing else to catch and so nobody had thought to add a
translation to it. A screen catching one would not catch the other.

**What was tried:** The end-to-end test found it, not by testing translation
but by asking every operation in turn rather than a representative one. Fixing
the inconsistent services one at a time was considered and rejected: five
copies of a decision cannot be kept in agreement, and the next person to add a
remote method would have had to notice that a translation was expected.

**What fixed it:** One `Refusals.translate` used by `DrsClient.sendOrThrow`,
which every request in the client passes through. The per-service translations
were deleted. Not found, conflict and validation become
`IllegalArgumentException`; access denied becomes `IllegalStateException`,
because nothing about the request was wrong; an expired session, a bad
signature and a storage failure travel on as they are, since presenting those
as the person's mistake would send them looking for an error they did not make.
`AccountManagementController` gained handling for the refusals the server can
now return, including one call that had no error handling at all.

**GitHub issue:** #

---

### 2026-10-16 Technical: the migration would have overwritten live reports without saying so

**Who hit it:** Nishadi

**What happened:** The data migration reads the Assessment 2 JSON files and
writes through the MySQL repositories. Saving a report is an upsert, keyed on
the reference number, and both systems allocate references from DR-0001
upwards. So migrating into a database that had been in use would not fail:
each migrated report would quietly replace a live one, and a disaster reported
that morning would become a disaster reported the month before. Nothing would
have reported a problem, and the counts returned would have looked correct.

**What was tried:** Making the migration skip a reference that already exists
was the first idea, which is what the protocol document originally specified.
It is wrong: it would leave the Assessment 2 reports silently unmigrated and
report them as skipped, which reads like a success. Renumbering the incoming
reports was also considered and rejected, because the response tasks, alerts
and audit entries already carried the old references.

**What fixed it:** The migration refuses, with `CONFLICT`, when the database
already holds any disaster report, and the message says to migrate into an
empty database and names the command that produces one. Idempotence is handled
separately and properly: the migration records itself in `schema_metadata`, so
a second run does nothing at all and reports the date of the first and what it
moved, rather than inferring from primary keys. PROTOCOL.md 5.7 was corrected
to match, in version 1.2, with the reasoning.

**GitHub issue:** #

---

### 2026-10-16 Collaborative: the frozen protocol and the code disagreed in four places

**Who was involved:** Nishadi, with the protocol document as the team's shared
contract

**What happened:** PROTOCOL.md was frozen in commit 5 so that three people
could build against it at once. Writing the client half of it was the first
time the document and the code had to agree field by field, and four
disagreements surfaced: `account.assignRole` was specified to return the
altered user, `audit.verifyChain` to return two fields, `admin.migrate` to take
a `confirm` flag and to infer idempotence from primary keys, and the version
number in the header still said 1.0 after version 1.1 had been agreed.

**How it was negotiated:** The rule applied was that the contract wins unless
there is a reason the contract cannot be met. Where the document's name was as
good as the code's, the code was changed: `account.create` now returns `user`,
`account.findAll` returns `users` and `account.activate` returns `changed`,
because those names were already written down and other members may have read
them.

Where the contract could not be met, the document was changed and the reason
recorded in the section it belongs to. `assignRole` returns an acknowledgement
because the local and remote implementations of the same interface must fail
and return alike, and the local one returns nothing. `verifyChain` returns a
status object with a `supported` field because the JSON store keeps no hash
chain and must be able to say that the question cannot be answered, rather than
claiming the trail is intact or reporting it as tampered with.

**What was agreed:** Version 1.2 of PROTOCOL.md, with a change log entry naming
each correction and its reason, so that the other two members can see what
moved and why without reading the code. The operations reserved for their two
features were not touched.

---
