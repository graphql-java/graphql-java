package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaSyntheticNonNull implements SchemaNonNull {

    private final SchemaType wrappedType;

    @Internal
    public SUSchemaSyntheticNonNull(SchemaType wrappedType) {
        this.wrappedType = assertNotNull(wrappedType);
    }

    @Override
    public SchemaType getWrappedType() {
        return wrappedType;
    }

}
