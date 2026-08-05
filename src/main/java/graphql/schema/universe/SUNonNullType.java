package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;

@ExperimentalApi
public final class SUNonNullType extends SUType {

    @Internal
    public SUNonNullType(int id) {
        super(id, 0, SUVertexKind.NON_NULL, null, null);
    }
}
