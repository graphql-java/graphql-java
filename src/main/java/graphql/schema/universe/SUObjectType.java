package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
@NullMarked
public final class SUObjectType extends SUNamedType {

    @Internal
    public SUObjectType(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.OBJECT, name, description);
    }
}
