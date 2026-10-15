package com.cqu.drs.client;

import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.migration.MigrationOutcome;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;

import java.io.Console;
import java.util.Optional;
import java.util.Scanner;

/**
 * Asks a running server to carry the Assessment 2 data into the database.
 *
 * Run it from the project directory, with the server already running:
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.cqu.drs.client.MigrationTool
 * </pre>
 *
 * or in NetBeans by right-clicking this file and choosing Run File. It asks
 * for an administrator's username and password, sends one request, and prints
 * what was moved.
 *
 * <h2>Why this is a client and not a script</h2>
 * It opens a socket to the server and signs in, exactly as the desktop
 * application does, and it holds no database connection of its own. That is
 * the point worth demonstrating: the migration goes through the server, is
 * authorised by a role the server checks, is signed like every other request,
 * and appears in the audit trail as something a named administrator did. A
 * script writing to MySQL directly would move the same rows and prove none of
 * that.
 *
 * <h2>Why the password is read from the console rather than an argument</h2>
 * A password passed on the command line is visible to anybody who can list
 * processes, and it stays in the shell's history afterwards. Where a real
 * console is available the typing is hidden as well.
 */
public final class MigrationTool {

    /** Not instantiable: this class is an entry point only. */
    private MigrationTool() {
    }

    /**
     * Entry point.
     *
     * @param args optionally the administrator's username; the password is
     *             always asked for rather than read from here
     */
    public static void main(String[] args) {
        DatabaseConfig config = DatabaseConfig.load();

        try (DrsClient client = new DrsClient(config)) {
            System.out.println("DRS-Enhanced data migration");
            System.out.println("Server: " + client.describeServer());
            System.out.println();

            String username = args.length > 0 ? args[0] : ask("Administrator username: ");
            String password = askSecret("Password: ");

            RemoteAuthenticationService authentication =
                    new RemoteAuthenticationService(client);

            Optional<User> signedIn = authentication.login(username, password);
            if (signedIn.isEmpty()) {
                System.err.println("Those details were not accepted.");
                System.exit(2);
                return;
            }
            if (signedIn.get().getRole() != UserRole.ADMINISTRATOR) {
                // Said here as well as enforced on the server, because being
                // told which account to use is more useful than being told the
                // request was refused.
                System.err.println("The migration may only be run by an "
                        + "administrator. " + username + " is a "
                        + signedIn.get().getRole().getLabel() + ".");
                authentication.logout();
                System.exit(3);
                return;
            }

            System.out.println();
            System.out.println("Asking the server to migrate. This may take a moment.");

            MigrationOutcome outcome =
                    new RemoteMigrationService(client).migrate(username);

            System.out.println();
            System.out.println(outcome.describe());
            authentication.logout();

            // Zero either way: a migration that had already been carried out
            // is a successful outcome, not a failure, and a script running
            // this twice should not stop.
            System.exit(0);

        } catch (IllegalArgumentException | IllegalStateException e) {
            // The server refused. Its message says what to do about it, so it
            // is printed as it is rather than wrapped in something vaguer.
            System.err.println();
            System.err.println("The migration was refused.");
            System.err.println(e.getMessage());
            System.exit(4);

        } catch (ServerUnavailableException e) {
            System.err.println();
            System.err.println("The server could not be reached. Start it with "
                    + "ServerMain and try again.");
            System.err.println(e.getMessage());
            System.exit(5);
        }
    }

    private static String ask(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        Scanner scanner = new Scanner(System.in);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    /**
     * Reads a password without echoing it where that is possible.
     *
     * {@code System.console()} returns null when the process is run from an
     * IDE, which is how this will usually be run, so there has to be a
     * fallback. The fallback echoes, and says so rather than letting someone
     * believe their typing was hidden.
     *
     * @param prompt what to ask
     * @return what was typed
     */
    private static String askSecret(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] typed = console.readPassword(prompt);
            return typed == null ? "" : new String(typed);
        }
        System.out.println("(This window cannot hide typing, so the password "
                + "will be visible.)");
        return ask(prompt);
    }
}
