package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaUnion;
import graphql.schema.universe.SUUnionType;

@Internal
public final class SUSchemaUnion
        extends AbstractSUSchemaNamedType implements SchemaUnion {

    @Internal
    public SUSchemaUnion(
            SUExecutableSchema executableSchema,
            SUUnionType type) {
        super(executableSchema, type);
    }
}
