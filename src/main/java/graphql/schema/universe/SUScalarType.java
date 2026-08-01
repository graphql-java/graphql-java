package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
@NullMarked
public final class SUScalarType extends SUNamedType {

    @Internal
    public SUScalarType(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.SCALAR, name, description);
    }
}
