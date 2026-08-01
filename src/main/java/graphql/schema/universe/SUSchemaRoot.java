package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
@NullMarked
public final class SUSchemaRoot extends SUVertex implements SUAppliedDirectiveContainer {

    @Internal
    public SUSchemaRoot(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.SCHEMA, name, description);
    }
}
