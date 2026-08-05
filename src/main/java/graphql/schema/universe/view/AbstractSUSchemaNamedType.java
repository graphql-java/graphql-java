package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaNamedType;
import graphql.schema.universe.SUNamedType;

import static graphql.Assert.assertNotNull;

/**
 * Shared base for schema-bound named type adapters.
 */
@Internal
public abstract class AbstractSUSchemaNamedType
        extends AbstractSUSchemaType implements SchemaNamedType {

    @Internal
    public AbstractSUSchemaNamedType(
            SUSchemaExecutableSchema executableSchema,
            SUNamedType type) {
        super(executableSchema, type);
    }

    @Override
    public final String getName() {
        return assertNotNull(getTypeVertex().getName());
    }
}
