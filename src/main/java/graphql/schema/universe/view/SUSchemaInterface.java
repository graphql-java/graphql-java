package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaInterface;
import graphql.schema.universe.SUInterfaceType;

@Internal
public final class SUSchemaInterface
        extends AbstractSUSchemaNamedType implements SchemaInterface {

    @Internal
    public SUSchemaInterface(
            SUSchemaExecutableSchema executableSchema,
            SUInterfaceType type) {
        super(executableSchema, type);
    }
}
