package graphql.execution.batched;

import graphql.PublicApi;

@Deprecated
@PublicApi
public class FetchedValue {

    private final MapOrList mapOrList;
    private final Object value;

    public FetchedValue(MapOrList parentResult, Object value) {
        this.mapOrList = parentResult;
        this.value = value;
    }

    public MapOrList getParentResult() {
        return mapOrList;
    }

    public Object getValue() {
        return value;
    }
}
