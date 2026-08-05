package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A named GraphQL type that can be the type condition of a selection set.
 */
@ExperimentalApi
@NullMarked
public abstract class SUCompositeType extends SUNamedType {

    @Internal
    public SUCompositeType(
            int id,
            int nameId,
            SUVertexKind kind,
            String name,
            @Nullable String description) {
        super(id, nameId, kind, name, description);
    }
}
