package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public final class SUEnumType extends SUNamedType {

    @Internal
    public SUEnumType(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.ENUM, name, description);
    }
}
