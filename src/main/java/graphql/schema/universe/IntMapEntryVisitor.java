package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * Visits one binding in a persistent integer map.
 *
 * @param <V> the stored value type
 */
@Internal
@NullMarked
@FunctionalInterface
public interface IntMapEntryVisitor<V> {

    /**
     * Visits one key and value.
     *
     * @param key the integer key
     * @param value the stored value
     */
    void visit(int key, V value);
}
