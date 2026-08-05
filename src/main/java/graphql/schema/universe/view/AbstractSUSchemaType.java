package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaType;
import graphql.schema.universe.SUType;

/**
 * Shared base for schema-bound universe type adapters.
 */
@Internal
public abstract class AbstractSUSchemaType
        extends AbstractSUSchemaElement implements SchemaType {

    @Internal
    public AbstractSUSchemaType(
            SUSchemaExecutableSchema executableSchema,
            SUType type) {
        super(executableSchema, type);
    }

    @Internal
    public final SUType getTypeVertex() {
        return (SUType) getVertex();
    }
}
