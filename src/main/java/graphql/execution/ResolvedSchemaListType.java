package graphql.execution;

import graphql.Internal;
import graphql.schema.SchemaList;
import graphql.schema.SchemaType;
import org.jspecify.annotations.NullMarked;

import static graphql.Assert.assertNotNull;

@Internal
@NullMarked
public final class ResolvedSchemaListType implements SchemaList {

    private final SchemaType wrappedType;

    public ResolvedSchemaListType(SchemaType wrappedType) {
        this.wrappedType = assertNotNull(wrappedType);
    }

    @Override
    public SchemaType getWrappedType() {
        return wrappedType;
    }
}
