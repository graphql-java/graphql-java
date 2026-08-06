package graphql.execution;

import graphql.Internal;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;
import org.jspecify.annotations.NullMarked;

import static graphql.Assert.assertNotNull;

@Internal
@NullMarked
public final class ResolvedSchemaNonNullType implements SchemaNonNull {

    private final SchemaType wrappedType;

    public ResolvedSchemaNonNullType(SchemaType wrappedType) {
        this.wrappedType = assertNotNull(wrappedType);
    }

    @Override
    public SchemaType getWrappedType() {
        return wrappedType;
    }
}
