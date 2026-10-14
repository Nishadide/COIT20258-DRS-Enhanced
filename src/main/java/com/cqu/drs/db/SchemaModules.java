package com.cqu.drs.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one place where the parts of the schema are listed.
 *
 * Each member writes their own {@link SchemaModule} in their own file and adds
 * a single line to the list below. Keeping the registration to one line means
 * the only merge conflict possible between three people working at once is a
 * conflict between three separate lines, which Git resolves without help.
 *
 * Order matters. A module whose tables hold a foreign key into another
 * module's tables must be registered after that module. Both new features
 * reference the core tables, so core is always first.
 */
public final class SchemaModules {

    /** Not instantiable: this class holds one list. */
    private SchemaModules() {
    }

    /**
     * Every module making up the DRS-Enhanced schema, in creation order.
     *
     * @return the modules
     */
    public static List<SchemaModule> all() {
        List<SchemaModule> modules = new ArrayList<>();

        // The tables inherited from the Assessment 2 domain model, plus the
        // audit chain and the request log. Owner: Nishadi.
        modules.add(new CoreSchema());

        // Resource allocation. Owner: Member 2.
        // Add the line below once ResourceSchema exists. Change nothing else
        // in this file.
        //   modules.add(new ResourceSchema());

        // Casualty registry. Owner: Member 3.
        // Add the line below once CasualtySchema exists. Change nothing else
        // in this file.
        //   modules.add(new CasualtySchema());

        return Collections.unmodifiableList(modules);
    }
}
