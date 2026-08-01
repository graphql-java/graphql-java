package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.schema.idl.SchemaPrinter;
import org.jspecify.annotations.NullMarked;

import static graphql.Assert.assertNotNull;

/**
 * Prints a {@link SUSchema} directly as canonical semantic SDL.
 *
 * <p>Literal values are preserved directly. Non-literal values for GraphQL specified scalars are
 * converted without a runtime schema; non-literal custom scalar values cannot be printed because
 * a schema universe does not retain scalar coercing implementations.</p>
 */
@ExperimentalApi
@NullMarked
public final class SUSchemaPrinter {

    private final SchemaPrinter schemaPrinter;

    public SUSchemaPrinter() {
        this(SchemaPrinter.Options.defaultOptions());
    }

    public SUSchemaPrinter(SchemaPrinter.Options options) {
        schemaPrinter = new SchemaPrinter(assertNotNull(options));
    }

    public String print(SUSchema schema) {
        return schemaPrinter.print(assertNotNull(schema));
    }
}
