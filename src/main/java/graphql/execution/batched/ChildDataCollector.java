package graphql.execution.batched;

import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildDataCollector {

    private final Map<String, List<GraphQLExecutionNodeDatum>> childDataByTypename = new HashMap<>();
    private final Map<String, GraphQLObjectType> childTypesByName = new HashMap<>();


    public void putChildData(GraphQLObjectType objectType, GraphQLExecutionNodeDatum datum) {
        childTypesByName.put(objectType.getName(), objectType);
        multimapPut(childDataByTypename, objectType.getName(), datum);
    }

    private <K, V> void multimapPut(Map<K, List<V>> map, K key, V value) {
        multimapEnsureKey(map, key);
        map.get(key).add(value);
    }

    private <K, V> void multimapEnsureKey(Map<K, List<V>> map, K key) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<>());
        }
    }

    private <K, V> List<V> multimapGet(Map<K, List<V>> map, K key) {
        multimapEnsureKey(map, key);
        return map.get(key);
    }

    public List<Entry> getEntries() {
        List<Entry> entries = new ArrayList<>();
        for (String childTypename : childTypesByName.keySet()) {
            GraphQLObjectType childType = childTypesByName.get(childTypename);
            List<GraphQLExecutionNodeDatum> childData = multimapGet(childDataByTypename, childTypename);
            entries.add(new Entry(childType, childData));
        }
        return entries;
    }

    public static class Entry {
        private final GraphQLObjectType objectType;
        private final List<GraphQLExecutionNodeDatum> data;

        public Entry(GraphQLObjectType objectType,
                     List<GraphQLExecutionNodeDatum> data) {
            this.objectType = objectType;
            this.data = data;
        }

        public GraphQLObjectType getObjectType() {
            return objectType;
        }

        public List<GraphQLExecutionNodeDatum> getData() {
            return data;
        }
    }

}
