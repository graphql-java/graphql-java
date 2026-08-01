package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A node in the persistent map from source vertex IDs to their complete outgoing edge sets.
 *
 * <p>The map is a bitmap-compressed hash-array mapped trie. Each branch consumes five bits of the
 * mixed key hash, so {@code shift} advances by five on every recursive call. Implementations are
 * immutable: an update returns either the same node for a no-op or a replacement node that shares
 * all unaffected descendants.</p>
 *
 * <p>A nullable node result represents an empty subtree. A missing value is represented by
 * {@code null} inside the trie and normalized to {@link PackedEdgeSet#empty()} by
 * {@link PersistentEdgeMap}.</p>
 */
@Internal
@NullMarked
public interface EdgeMapNode {

    /**
     * Finds the outgoing edges for {@code key}.
     *
     * @param key the source vertex ID
     * @param hash the mixed key hash
     * @param shift the number of hash bits already consumed
     *
     * @return the stored edge set, or {@code null} when the key is absent
     */
    @Nullable PackedEdgeSet get(int key, int hash, int shift);

    /**
     * Associates {@code key} with {@code value}.
     *
     * @param key the source vertex ID
     * @param hash the mixed key hash
     * @param value the complete non-empty outgoing edge set
     * @param shift the number of hash bits already consumed
     *
     * @return this node for an identity no-op, otherwise a structurally shared replacement
     */
    EdgeMapNode put(int key, int hash, PackedEdgeSet value, int shift);

    /**
     * Removes {@code key}.
     *
     * @param key the source vertex ID
     * @param hash the mixed key hash
     * @param shift the number of hash bits already consumed
     *
     * @return this node when the key is absent, a replacement node, or {@code null} if empty
     */
    @Nullable EdgeMapNode remove(int key, int hash, int shift);
}
