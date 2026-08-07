package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;
import graphql.schema.universe.SUNonNullType;
import graphql.schema.universe.SUType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaNonNull
        extends AbstractSUSchemaType implements SchemaNonNull {

    @Internal
    public SUSchemaNonNull(
            SUExecutableSchema executableSchema,
            SUNonNullType type) {
        super(executableSchema, type);
    }

    @Override
    public SchemaType getWrappedType() {
        SUType wrappedType = assertNotNull(
                getExecutableSchema()
                        .getSchema()
                        .getWrappedType(getNonNullTypeVertex()));
        return getExecutableSchema().adaptType(wrappedType);
    }

    @Internal
    public SUNonNullType getNonNullTypeVertex() {
        return (SUNonNullType) getTypeVertex();
    }
}
