package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public final class SUInputObjectType extends SUNamedType {

    @Internal
    public SUInputObjectType(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.INPUT_OBJECT, name, description);
    }
}
