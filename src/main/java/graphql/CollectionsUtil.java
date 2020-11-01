package graphql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

@Internal
public final class CollectionsUtil {

    @SuppressWarnings("unchecked")
    public static <T> ImmutableList<T> emptyList() {
        return ImmutableList.of();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> ImmutableMap<K, V> emptyMap() {
        return ImmutableMap.of();
    }


    public static <K, V> ImmutableMap<K, V> addToMap(Map<K, V> existing, K newKey, V newVal) {
        return ImmutableMap.<K, V>builder().putAll(existing).put(newKey, newVal).build();
    }

    public static <K, V> ImmutableMap<K, V> mergeMaps(Map<K, V> m1, Map<K, V> m2) {
        return ImmutableMap.<K, V>builder().putAll(m1).putAll(m2).build();
    }

    public static <T> ImmutableList<T> mergeLists(List<T> l1, List<T> l2) {
        return ImmutableList.<T>builder().addAll(l1).addAll(l2).build();
    }
}
