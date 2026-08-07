package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public final class SUEnumValue extends SUVertex implements SUAppliedDirectiveContainer {

    @Internal
    public SUEnumValue(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.ENUM_VALUE, name, description);
    }
}
