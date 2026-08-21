package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * An immutable terminal mapping from one integer key to a value.
 *
 * @param <V> the stored value type
 */
@Internal
public final class IntMapLeaf<V> implements IntMapNode<V> {

    private final int key;
    private final int hash;
    private final V value;

    /**
     * Creates a leaf.
     *
     * @param key the integer key
     * @param hash the mixed key hash
     * @param value the value
     */
    public IntMapLeaf(int key, int hash, V value) {
        this.key = key;
        this.hash = hash;
        this.value = value;
    }

    @Override
    public @Nullable V get(int requestedKey, int requestedHash, int shift) {
        return key == requestedKey ? value : null;
    }

    @Override
    public IntMapNode<V> put(int requestedKey, int requestedHash, V requestedValue, int shift) {
        if (key == requestedKey) {
            return value == requestedValue
                    ? this
                    : new IntMapLeaf<>(key, hash, requestedValue);
        }
        IntMapLeaf<V> other = new IntMapLeaf<>(
                requestedKey,
                requestedHash,
                requestedValue);
        return IntMapBranch.merge(this, other, shift);
    }

    @Override
    public @Nullable IntMapNode<V> remove(int requestedKey, int requestedHash, int shift) {
        return key == requestedKey ? null : this;
    }

    @Override
    public void visitEntries(
            Set<IntMapNode<V>> visitedNodes,
            IntMapEntryVisitor<V> visitor) {
        if (!visitedNodes.add(this)) {
            return;
        }
        visitor.visit(key, value);
    }

    @Override
    public void forEachEntry(IntMapEntryVisitor<V> visitor) {
        visitor.visit(key, value);
    }

    /**
     * Returns the mixed hash used to place this leaf in the trie.
     *
     * @return the mixed key hash
     */
    public int getHash() {
        return hash;
    }
}
