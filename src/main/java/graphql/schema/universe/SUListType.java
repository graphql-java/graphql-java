package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;

@ExperimentalApi
@NullMarked
public final class SUListType extends SUType {

    @Internal
    public SUListType(int id) {
        super(id, 0, SUVertexKind.LIST, null, null);
    }
}
