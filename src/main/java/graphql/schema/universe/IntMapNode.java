package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A node in a persistent integer-keyed hash-array mapped trie.
 *
 * <p>Each branch consumes five bits of the mixed key hash. Implementations are immutable: an
 * update returns either the same node for an identity no-op or a replacement sharing unaffected
 * descendants.</p>
 *
 * @param <V> the stored value type
 */
@Internal
@NullMarked
public interface IntMapNode<V> {

    /**
     * Finds a value.
     *
     * @param key the integer key
     * @param hash the mixed key hash
     * @param shift the number of hash bits already consumed
     *
     * @return the stored value, or {@code null} when absent
     */
    @Nullable V get(int key, int hash, int shift);

    /**
     * Associates a key with a non-null value.
     *
     * @param key the integer key
     * @param hash the mixed key hash
     * @param value the value
     * @param shift the number of hash bits already consumed
     *
     * @return this node for an identity no-op, otherwise a replacement
     */
    IntMapNode<V> put(int key, int hash, V value, int shift);

    /**
     * Removes a key.
     *
     * @param key the integer key
     * @param hash the mixed key hash
     * @param shift the number of hash bits already consumed
     *
     * @return this node when absent, a replacement node, or {@code null} if empty
     */
    @Nullable IntMapNode<V> remove(int key, int hash, int shift);

    /**
     * Visits entries below this node unless it has already been visited through another map.
     *
     * @param visitedNodes identity-based set of previously visited nodes
     * @param visitor the entry visitor
     */
    void visitEntries(
            Set<IntMapNode<V>> visitedNodes,
            IntMapEntryVisitor<V> visitor);
}
