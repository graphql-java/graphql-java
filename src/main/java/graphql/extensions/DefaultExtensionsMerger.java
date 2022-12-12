package graphql.extensions;

import com.google.common.collect.Sets;
import graphql.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Internal
public class DefaultExtensionsMerger implements ExtensionsMerger {
    @Override
    @NotNull
    public Map<Object, Object> merge(@NotNull Map<Object, Object> leftMap, @NotNull Map<Object, Object> rightMap) {
        if (leftMap.isEmpty()) {
            return mapCast(rightMap);
        }
        if (rightMap.isEmpty()) {
            return mapCast(leftMap);
        }
        Map<Object, Object> targetMap = new LinkedHashMap<>();
        Set<Object> leftKeys = leftMap.keySet();
        for (Object key : leftKeys) {
            Object leftVal = leftMap.get(key);
            if (rightMap.containsKey(key)) {
                Object rightVal = rightMap.get(key);
                targetMap.put(key, mergeObjects(leftVal, rightVal));
            } else {
                targetMap.put(key, leftVal);
            }
        }
        Sets.SetView<Object> rightOnlyKeys = Sets.difference(rightMap.keySet(), leftKeys);
        for (Object key : rightOnlyKeys) {
            Object rightVal = rightMap.get(key);
            targetMap.put(key, rightVal);
        }
        return targetMap;
    }

    private Object mergeObjects(Object leftVal, Object rightVal) {
        if (leftVal instanceof Map && rightVal instanceof Map) {
            return merge(mapCast(leftVal), mapCast(rightVal));
        } else if (leftVal instanceof Collection && rightVal instanceof Collection) {
            // we append - no equality or merging here
            return appendLists(leftVal, rightVal);
        } else {
            // we have some primitive - so prefer the right since it was encountered last
            // and last write wins here
            return rightVal;
        }
    }

    @NotNull
    private List<Object> appendLists(Object leftVal, Object rightVal) {
        List<Object> target = new ArrayList<>(listCast(leftVal));
        target.addAll(listCast(rightVal));
        return target;
    }

    private Map<Object, Object> mapCast(Object map) {
        //noinspection unchecked
        return (Map<Object, Object>) map;
    }

    private Collection<Object> listCast(Object collection) {
        //noinspection unchecked
        return (Collection<Object>) collection;
    }
}
