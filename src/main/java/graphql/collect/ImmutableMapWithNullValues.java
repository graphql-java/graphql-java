package graphql.collect;

import graphql.Assert;
import graphql.DeprecatedAt;
import graphql.Internal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The standard ImmutableMap does not allow null values. The implementation does.
 * We have cases in graphql, around arguments where a map entry can be explicitly set to null
 * and we want immutable smart maps for these cases.
 *
 * @param <K> for key
 * @param <V> for victory
 */
@SuppressWarnings({"NullableProblems", "unchecked", "rawtypes"})
@Internal
public final class ImmutableMapWithNullValues<K, V> implements Map<K, V> {

    private final Map<K, V> delegate;

    private static final ImmutableMapWithNullValues emptyMap = new ImmutableMapWithNullValues();

    private ImmutableMapWithNullValues(Map<K, V> values) {
        this.delegate = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Only used to construct the singleton empty map
     */
    private ImmutableMapWithNullValues() {
        this(ImmutableKit.emptyMap());
    }


    public static <K, V> ImmutableMapWithNullValues<K, V> emptyMap() {
        return emptyMap;
    }

    public static <K, V> ImmutableMapWithNullValues<K, V> copyOf(Map<K, V> map) {
        Assert.assertNotNull(map);
        if (map instanceof ImmutableMapWithNullValues) {
            return (ImmutableMapWithNullValues<K, V>) map;
        }
        if (map.isEmpty()) {
            return emptyMap();
        }
        return new ImmutableMapWithNullValues<>(map);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach(action);
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    @DeprecatedAt("2020-11-10")
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
