package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * An immutable terminal mapping from one source vertex ID to its outgoing edges.
 *
 * <p>The mixed hash is retained so that two leaves can be separated into the required branch
 * levels without hashing either key again. Leaves compare the original key on lookup, making the
 * key authoritative after trie navigation has selected a candidate leaf.</p>
 */
@Internal
@NullMarked
public final class EdgeMapLeaf implements EdgeMapNode {

    private final int key;
    private final int hash;
    private final PackedEdgeSet value;

    /**
     * Creates a leaf.
     *
     * @param key the source vertex ID
     * @param hash the mixed key hash
     * @param value the complete outgoing edge set for the source
     */
    public EdgeMapLeaf(int key, int hash, PackedEdgeSet value) {
        this.key = key;
        this.hash = hash;
        this.value = value;
    }

    @Override
    public @Nullable PackedEdgeSet get(int requestedKey, int requestedHash, int shift) {
        return key == requestedKey ? value : null;
    }

    @Override
    public EdgeMapNode put(int requestedKey, int requestedHash, PackedEdgeSet requestedValue, int shift) {
        if (key == requestedKey) {
            return value == requestedValue ? this : new EdgeMapLeaf(key, hash, requestedValue);
        }
        EdgeMapLeaf other = new EdgeMapLeaf(requestedKey, requestedHash, requestedValue);
        return EdgeMapBranch.merge(this, other, shift);
    }

    @Override
    public @Nullable EdgeMapNode remove(int requestedKey, int requestedHash, int shift) {
        return key == requestedKey ? null : this;
    }

    @Override
    public void visitEntries(
            Set<EdgeMapNode> visitedNodes,
            EdgeMapEntryVisitor visitor) {
        if (!visitedNodes.add(this)) {
            return;
        }
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
