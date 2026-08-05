package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaList;
import graphql.schema.SchemaType;
import graphql.schema.universe.SUListType;
import graphql.schema.universe.SUType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaList
        extends AbstractSUSchemaType implements SchemaList {

    @Internal
    public SUSchemaList(
            SUSchemaExecutableSchema executableSchema,
            SUListType type) {
        super(executableSchema, type);
    }

    @Override
    public SchemaType getWrappedType() {
        SUType wrappedType = assertNotNull(
                getExecutableSchema()
                        .getSchema()
                        .getWrappedType(getListTypeVertex()));
        return getExecutableSchema().adaptType(wrappedType);
    }

    @Internal
    public SUListType getListTypeVertex() {
        return (SUListType) getTypeVertex();
    }
}
