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
