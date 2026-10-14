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
