package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaObject;
import graphql.schema.universe.SUObjectType;

@Internal
public final class SUSchemaObject
        extends AbstractSUSchemaNamedType implements SchemaObject {

    @Internal
    public SUSchemaObject(
            SUExecutableSchema executableSchema,
            SUObjectType type) {
        super(executableSchema, type);
    }
}
