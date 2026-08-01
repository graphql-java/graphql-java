package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A named GraphQL type vertex.
 */
@ExperimentalApi
@NullMarked
public abstract class SUNamedType extends SUType implements SUAppliedDirectiveContainer {

    @Internal
    protected SUNamedType(
            int id,
            int nameId,
            SUVertexKind kind,
            String name,
            @Nullable String description) {
        super(id, nameId, kind, name, description);
    }
}
