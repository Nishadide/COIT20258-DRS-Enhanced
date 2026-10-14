DRS-Initial  |  Disaster Response System
COIT20258 Software Engineering, Assessment 2, Term 2 2026
Nishadi de Zoysa, student 12305815

------------------------------------------------------------------------
RUNNING IT
------------------------------------------------------------------------
Requirements : Java 21 and Maven. JavaFX 21.0.2, Gson 2.10.1 and JUnit 5
               are fetched by Maven on the first build. No database and no
               network connection are needed.

NetBeans     : File > Open Project, select this folder. It is recognised
               as a Maven project.
               Right-click the project > Clean and Build.
               Right-click the project > Run Maven > Goals, enter:
                   javafx:run

Command line : mvn clean javafx:run

Tests        : right-click the project > Test, or  mvn test
               Expected output:  Tests run: 192, Failures: 0, Errors: 0

------------------------------------------------------------------------
SIGNING IN
------------------------------------------------------------------------
  Username      Password    Role
  ------------  ----------  --------------------------------------------
  admin         admin123    System Administrator
  coordinator   coord123    Emergency Coordinator
  fire.crew     crew1234    Department Responder (Fire and Emergency)

Reporting a disaster and checking a report need NO account. Both are
reached from the welcome screen before signing in.

------------------------------------------------------------------------
THE data FOLDER
------------------------------------------------------------------------
Data is stored as JSON in a folder named "data" beside the project.

If this download includes a data folder, it holds a prepared set of
disasters so the application has something to show straight away. To see
the system create its own starting state instead, delete the data folder
and launch again: the eight departments and the three accounts above are
seeded automatically on a first run.

------------------------------------------------------------------------
A FIVE MINUTE TOUR, BY MARKING CRITERION
------------------------------------------------------------------------
1. Reporting different kinds of disasters
   Welcome screen > Report a disaster. No sign-in. Choose one of five
   disaster types, a severity and an address. Submit, note the reference
   (DR-nnnn), then Check a report and enter it.

2. Assessment and prioritised response
   Sign in as coordinator. Select a report awaiting assessment and press
   Assess. Choosing a severity shows the priority it will produce BEFORE
   the assessment is committed. Record it, and the dashboard reorders.

3. Coordinating departments and people
   Coordinator > select an assessed disaster > Coordinate. Click a
   department, hold Ctrl and click a second, press Dispatch. Then sign in
   as fire.crew to update the task, and back as coordinator to Resolve.

   The failure path: sign in as admin, toggle a department unavailable,
   dispatch to it as coordinator, and the notice is recorded as failed.
   Close that task with "Record follow-up on selected task".

4. Two creative features
   a) Reporting without an account, above. The stored record shows
      "reporter": null in data/reports.json.
   b) Coordinates and proximity. Report two disasters with "Use my
      location" ticked at nearby points, then select one on the dashboard
      and press "Nearby reports". It lists the others within five
      kilometres, nearest first, with the distance to each.

Appendix A of the report gives the full steps, the classes behind each
feature, and the figures and tests that evidence it.

------------------------------------------------------------------------
STRUCTURE
------------------------------------------------------------------------
  src/main/java/com/cqu/drs/
      model/        domain classes and enumerations
      service/      business rules (7 services)
      repository/   JSON storage (5 repositories and one store)
      controller/   one controller per screen (10)
  src/main/resources/fxml/   10 layouts, authored in Scene Builder
  src/main/resources/css/    one shared stylesheet
  src/test/java/             17 test classes, 192 tests

Dependencies run downward only: a controller may call a service and a
service may call a repository, but nothing calls upward.
