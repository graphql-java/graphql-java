package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;

@ExperimentalApi
public final class SUListType extends SUType {

    @Internal
    public SUListType(int id) {
        super(id, 0, SUVertexKind.LIST, null, null);
    }
}
