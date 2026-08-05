package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaScalar;
import graphql.schema.universe.SUScalarType;

@Internal
public final class SUSchemaScalar
        extends AbstractSUSchemaNamedType implements SchemaScalar {

    @Internal
    public SUSchemaScalar(
            SUSchemaExecutableSchema executableSchema,
            SUScalarType type) {
        super(executableSchema, type);
    }
}
