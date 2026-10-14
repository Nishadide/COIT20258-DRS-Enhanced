# Task distribution

COIT20258 Assessment 3, DRS-Enhanced.

## Team

| Role | Name | Student ID | GitHub |
| --- | --- | --- | --- |
| Server and database | Nishadi de Zoysa | 12305815 | Nishadide |
| Resource allocation feature | [Member 2 name] | [ID] | [username] |
| Casualty registry and data security | [Member 3 name] | [ID] | [username] |

## How the work is split

Each member owns a vertical slice end to end: the code, its tests, its
screenshots and its sections of the group report. Nobody owns documentation
alone, because the marking criteria reward commits related to assigned tasks
and a documentation-only member has nothing to commit.

Nishadi owns the spine, which the other two build on, so it starts first and
must not slip. The two features were chosen to make the multi-threaded server
and the data security requirements demonstrable, since neither earns marks of
its own on the marking sheet.

## Nishadi: server, database and integration

- [ ] Initial import: DRS-Initial selected as base
- [ ] Extract repository interfaces so storage can be swapped
- [ ] Document selection of DRS-Initial with justification
- [ ] Add task assignments and problem log
- [ ] Define client-server protocol
- [ ] Add MySQL driver dependency
- [ ] Add schema creation for MySQL
- [ ] Add multi-threaded server with thread-per-client
- [ ] Add MySQL DAO for disaster reports
- [ ] Walking skeleton: client to server to MySQL
- [ ] Add three-tier architecture diagram for DRS-Enhanced
- [ ] Add MySQL DAOs for users, departments, alerts and audit
- [ ] Add client-side service proxies over the protocol
- [ ] Load DRS-Initial data through the server into MySQL
- [ ] Add SQL script to create and populate the database
- [ ] Document MVC roles after the client-server split
- [ ] Integrate features and fix defects
- [ ] Add screenshot-based run instructions
- [ ] Assemble group report

## Member 2: resource allocation

Departments hold finite resources. A coordinator requests them for a disaster,
and the pool is shared across every active disaster, so two coordinators can
compete for the last unit. This feature is where the multi-threaded server is
demonstrated.

- [ ] Add task assignments for resource allocation
- [ ] Add requirements for resource allocation
- [ ] Correct requirements invalidated by the revised architecture
- [ ] Add use case diagram for DRS-Enhanced
- [ ] Add sequence diagram for resource allocation
- [ ] Add Resource, ResourceType and ResourceAllocation
- [ ] Add resource allocation service with transaction
- [ ] Add resource allocation screen
- [ ] Add concurrency test: two clients, one unit
- [ ] Add test plan rows for resource allocation
- [ ] Add screenshots for resource allocation
- [ ] Add user requirements with screenshots

## Member 3: casualty registry and data security

Casualties are registered against a disaster, triaged, and referred to a
hospital. The records are sensitive personal data, which is where the access
rights, time stamping, encryption, decryption and non-repudiation required by
the specification are implemented and demonstrated.

- [ ] Add task assignments for casualty registry
- [ ] Add requirements for casualty registry
- [ ] Add security requirements for data privacy
- [ ] Add actual results column to the test plan
- [ ] Add class diagram for DRS-Enhanced
- [ ] Add ERD for the MySQL database
- [ ] Add sequence diagram for casualty registry
- [ ] Add Casualty and TriageCategory
- [ ] Add casualty registry service
- [ ] Add casualty registration screen
- [ ] Add encryption at rest for casualty records
- [ ] Add non-repudiable audit entries
- [ ] Add test plan rows for casualty registry
- [ ] Add screenshots for casualty registry
- [ ] Finalise non-functional requirements

## Rules the team follows

- Every commit message names the task it delivers.
- Each member pushes under their own GitHub account.
- Problems are appended to PROBLEM-LOG.md as they happen, not afterwards.
- Issues are raised and closed in GitHub, so the trail of problems found and
  resolved is visible.
- A slice is done when it compiles, its tests pass, its screenshots are taken
  and its report section is written. Not when the code works.

## Gates

Work does not move past a gate until it closes.

| Gate | Condition |
| --- | --- |
| Protocol | PROTOCOL.md is pushed. No client code before it exists |
| Skeleton | One operation travels client to server to MySQL and back |
| Integration | Everything merged to main and the application launches |
| Freeze | No new features. Integration, testing and screenshots only |
