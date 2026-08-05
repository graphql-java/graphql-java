package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A GraphQL type vertex, including named types and list/non-null wrappers.
 */
@ExperimentalApi
public abstract class SUType extends SUVertex {

    @Internal
    protected SUType(
            int id,
            int nameId,
            SUVertexKind kind,
            @Nullable String name,
            @Nullable String description) {
        super(id, nameId, kind, name, description);
    }
}
