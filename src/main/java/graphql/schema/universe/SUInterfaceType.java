package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public final class SUInterfaceType extends SUCompositeType {

    @Internal
    public SUInterfaceType(int id, int nameId, String name, @Nullable String description) {
        super(id, nameId, SUVertexKind.INTERFACE, name, description);
    }
}
