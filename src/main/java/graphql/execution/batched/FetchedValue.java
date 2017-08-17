package graphql.execution.batched;

public class FetchedValue {

    private final MapOrList mapOrList;
    private final Object value;

    public FetchedValue(MapOrList mapOrList, Object value) {
        this.mapOrList = mapOrList;
        this.value = value;
    }

    public MapOrList getResultContainer() {
        return mapOrList;
    }

    public Object getValue() {
        return value;
    }
}
