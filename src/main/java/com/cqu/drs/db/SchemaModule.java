package com.cqu.drs.db;

import java.util.List;

/**
 * One group of related tables, contributed by one part of the system.
 *
 * The schema is divided this way for a practical reason. Three members are
 * working at the same time and two of them add tables of their own. If every
 * CREATE TABLE statement lived in one class, all three would edit that class
 * and every merge would conflict. Instead each member writes their own
 * implementation of this interface in their own file, and the only shared line
 * is the registration in {@link SchemaModules}.
 *
 * It also keeps each member's commits confined to their own assigned task,
 * which is what the assessment asks for.
 *
 * <p>Implementations must obey two rules:
 *
 * <ol>
 *   <li>Every statement uses {@code CREATE TABLE IF NOT EXISTS}, so running
 *       the initialiser against a database that already holds the tables does
 *       nothing rather than failing.</li>
 *   <li>Statements are returned in dependency order. A table carrying a
 *       foreign key must appear after the table it points at, because MySQL
 *       rejects a foreign key to a table that does not yet exist.</li>
 * </ol>
 */
public interface SchemaModule {

    /**
     * A short name for this group of tables, used in the initialiser's report
     * so that a failure can be traced to the member who owns it.
     *
     * @return the module name, such as "core" or "resource allocation"
     */
    String name();

    /**
     * The CREATE TABLE statements this module contributes, in dependency
     * order.
     *
     * @return the statements, each without a trailing semicolon
     */
    List<String> tableDefinitions();
}
