package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public final class SUField extends SUVertex implements SUAppliedDirectiveContainer {

    @Internal
    public SUField(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.FIELD, name, description);
    }
}
