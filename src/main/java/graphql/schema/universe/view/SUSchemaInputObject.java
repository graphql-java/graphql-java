package graphql.schema.universe.view;

import graphql.Directives;
import graphql.Internal;
import graphql.schema.SchemaInputObject;
import graphql.schema.universe.SUInputObjectType;

@Internal
public final class SUSchemaInputObject
        extends AbstractSUSchemaNamedType implements SchemaInputObject {

    @Internal
    public SUSchemaInputObject(
            SUExecutableSchema executableSchema,
            SUInputObjectType type) {
        super(executableSchema, type);
    }

    @Override
    public boolean isOneOf() {
        return !getExecutableSchema()
                .getSchema()
                .getAppliedDirectives(
                        getInputObjectTypeVertex(),
                        Directives.OneOfDirective.getName())
                .isEmpty();
    }

    @Internal
    public SUInputObjectType getInputObjectTypeVertex() {
        return (SUInputObjectType) getTypeVertex();
    }
}
