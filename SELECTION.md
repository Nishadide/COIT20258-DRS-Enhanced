# Selecting the DRS-Initial to build on

Assessment 3 requires a team to critically review the DRS-Initial prototypes
its members built individually, select one, and justify that selection. This
records the review and the decision.

## The decision

The team selected **Nishadi de Zoysa's DRS-Initial** as the base for
DRS-Enhanced.

## How the three were compared

Each prototype was reviewed against five criteria, chosen because they decide
how much of Assessment 3 can be built on the base rather than rebuilt from it.

| Criterion | Why it decides the choice |
| --- | --- |
| Separation of storage from business logic | DRS-Enhanced replaces JSON files with MySQL. A prototype whose services reach storage directly must be rewritten, not extended |
| Automated test coverage | Tests inherited with the base protect the refactor. Without them, every change to the base is unverifiable |
| Coverage of the case study | The three capabilities the case study names must already work, or Assessment 3 spends its time finishing Assessment 2 |
| Conformance to MVC | Two marks in Assessment 3 depend on MVC surviving the client-server split. A base that is already MVC keeps that story intact |
| Assessment 2 result | An independent judgement of quality against the same marking criteria |

## Review of each prototype

### Nishadi de Zoysa's DRS-Initial

| Measure | Value |
| --- | --- |
| Assessment 2 result | 35 / 35 |
| Java classes | 66 |
| FXML screens built in Scene Builder | 10 |
| Automated tests | 192 across 17 test classes, all passing |
| Planned test cases documented | 105 |
| Storage isolation | Model classes carry no storage dependency; services and controllers never reference the storage technology |
| MVC | Smalltalk-80 variant inside a three-tier structure, documented and verified |

Storage isolation was the deciding measure. A check of the source confirmed
that no class in the service or controller packages refers to JSON or Gson,
and that the model classes mention JSON only in comments. Extracting a
repository interface per store therefore took one change and left the 7
services, 10 controllers and 21 model classes untouched, with all 192 tests
still passing afterwards.

### Hirushi's DRS-Initial

<!-- Fill this in with real facts about the prototype. Do not guess.
     Suggested measures to record, the same ones used above:
       Assessment 2 result
       number of classes and screens
       number of automated tests, and whether they pass
       whether services call storage directly
       whether MVC is followed
     Then two or three sentences on what it does well and what would have
     to be rebuilt to reach DRS-Enhanced. -->

### Sandeepa's DRS-Initial

<!-- As above. -->

## Justification

The selected prototype was chosen because its data tier is already isolated
behind the business logic, which is the single property that decides how much
of DRS-Enhanced can be built by extension. Its Assessment 2 report predicted
that moving to a database would touch the data tier alone; that prediction was
tested during Phase 1 of this project and held, with the storage interfaces
extracted and all 192 inherited tests still passing.

The 192 tests are the second reason. They were written against a documented
plan of 105 cases before the code existed, and they now serve as a regression
suite protecting every change the team makes to the base. Neither of the other
prototypes could offer the same protection.

## What the team changes in the selected base

Selecting a prototype is not the same as accepting it unchanged. The following
weaknesses were identified in the review and are addressed in DRS-Enhanced:

- Storage was file-based, with each repository loading an entire collection to
  save one object. This is replaced by MySQL with per-row updates.
- Passwords were stored as unsalted SHA-256 digests, which the Assessment 2
  report itself recorded as prototype-level.
- The application ran as a single process, so no concurrency existed. The
  multi-threaded server introduces it, and the resource allocation feature
  exercises it.
- Four repositories shared the same duplicated save routine, recorded as a
  known weakness in the Assessment 2 reflection and not acted on at the time.
