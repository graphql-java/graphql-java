package graphql.execution.batched;

import graphql.schema.GraphQLObjectType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildDataCollector {

    private final Map<String, List<GraphqlExecutionNodeDatum>> childDataByTypename = new HashMap<>();
    private final Map<String, GraphQLObjectType> childTypesByName = new HashMap<>();


    public void putChildData(GraphQLObjectType objectType, GraphqlExecutionNodeDatum datum) {
        childTypesByName.put(objectType.getName(), objectType);
        multimapPut(childDataByTypename, objectType.getName(), datum);
    }

    private <K, V> void multimapPut(Map<K, List<V>> map, K key, V value) {
        multimapEnsureKey(map, key);
        map.get(key).add(value);
    }

    private <K, V> void multimapEnsureKey(Map<K, List<V>> map, K key) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<V>());
        }
    }

    private <K, V> List<V> multimapGet(Map<K, List<V>> map, K key) {
        multimapEnsureKey(map, key);
        return map.get(key);
    }

    public List<Entry> getEntries() {
        List<Entry> entries = new ArrayList<>();
        for (String childTypename: childTypesByName.keySet()) {
            GraphQLObjectType childType = childTypesByName.get(childTypename);
            List<GraphqlExecutionNodeDatum> childData = multimapGet(childDataByTypename, childTypename);
            entries.add(new Entry(childType, childData));
        }
        return entries;
    }

    public static class Entry {
        private final GraphQLObjectType objectType;
        private final List<GraphqlExecutionNodeDatum> data;

        public Entry(GraphQLObjectType objectType,
                List<GraphqlExecutionNodeDatum> data) {
            this.objectType = objectType;
            this.data = data;
        }

        public GraphQLObjectType getObjectType() {
            return objectType;
        }

        public List<GraphqlExecutionNodeDatum> getData() {
            return data;
        }
    }

}
