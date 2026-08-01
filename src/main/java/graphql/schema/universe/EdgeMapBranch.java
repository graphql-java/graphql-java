package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * An immutable, bitmap-compressed 32-way branch in the persistent edge map.
 *
 * <p>Each bit in {@code bitmap} denotes an occupied logical slot selected by five hash bits.
 * {@code children} is a dense array containing only occupied slots, in ascending slot order. The
 * array index for a slot is the number of set bits preceding it.</p>
 *
 * <p>Updates copy only this branch's dense child array and replace the affected child. All other
 * child nodes remain shared with the previous map version.</p>
 */
@Internal
@NullMarked
public final class EdgeMapBranch implements EdgeMapNode {

    private final int bitmap;
    private final EdgeMapNode[] children;

    /**
     * Creates a branch from a bitmap and its dense, slot-ordered child array.
     *
     * @param bitmap the occupied logical slots
     * @param children children for the occupied slots, in ascending slot order
     */
    public EdgeMapBranch(int bitmap, EdgeMapNode[] children) {
        this.bitmap = bitmap;
        this.children = children;
    }

    /**
     * Builds the minimum branch path needed to distinguish two leaves.
     *
     * <p>If both hashes select the same slot at this depth, another single-child branch is created
     * and the next five hash bits are examined. {@link PersistentEdgeMap}'s hash mixer is
     * one-to-one for integer keys, so distinct source IDs eventually select different slots and no
     * separate full-hash collision node is needed.</p>
     *
     * @param first the first leaf
     * @param second the second leaf
     * @param shift the number of hash bits already consumed
     *
     * @return a branch containing both leaves
     */
    public static EdgeMapNode merge(EdgeMapLeaf first, EdgeMapLeaf second, int shift) {
        int firstSlot = slot(first.getHash(), shift);
        int secondSlot = slot(second.getHash(), shift);
        if (firstSlot == secondSlot) {
            EdgeMapNode child = merge(first, second, shift + 5);
            return new EdgeMapBranch(bit(firstSlot), new EdgeMapNode[]{child});
        }
        int combinedBitmap = bit(firstSlot) | bit(secondSlot);
        EdgeMapNode[] children = firstSlot < secondSlot
                ? new EdgeMapNode[]{first, second}
                : new EdgeMapNode[]{second, first};
        return new EdgeMapBranch(combinedBitmap, children);
    }

    @Override
    public @Nullable PackedEdgeSet get(int key, int hash, int shift) {
        int bit = bit(slot(hash, shift));
        if ((bitmap & bit) == 0) {
            return null;
        }
        return children[index(bit)].get(key, hash, shift + 5);
    }

    @Override
    public EdgeMapNode put(int key, int hash, PackedEdgeSet value, int shift) {
        int bit = bit(slot(hash, shift));
        int index = index(bit);
        if ((bitmap & bit) == 0) {
            return insert(bit, index, new EdgeMapLeaf(key, hash, value));
        }
        EdgeMapNode oldChild = children[index];
        EdgeMapNode newChild = oldChild.put(key, hash, value, shift + 5);
        if (oldChild == newChild) {
            return this;
        }
        EdgeMapNode[] newChildren = Arrays.copyOf(children, children.length);
        newChildren[index] = newChild;
        return new EdgeMapBranch(bitmap, newChildren);
    }

    @Override
    public @Nullable EdgeMapNode remove(int key, int hash, int shift) {
        int bit = bit(slot(hash, shift));
        if ((bitmap & bit) == 0) {
            return this;
        }
        int index = index(bit);
        EdgeMapNode oldChild = children[index];
        EdgeMapNode newChild = oldChild.remove(key, hash, shift + 5);
        if (oldChild == newChild) {
            return this;
        }
        if (newChild != null) {
            EdgeMapNode[] newChildren = Arrays.copyOf(children, children.length);
            newChildren[index] = newChild;
            return new EdgeMapBranch(bitmap, newChildren);
        }
        return removeChild(bit, index);
    }

    private EdgeMapNode insert(int bit, int index, EdgeMapNode child) {
        EdgeMapNode[] newChildren = new EdgeMapNode[children.length + 1];
        System.arraycopy(children, 0, newChildren, 0, index);
        newChildren[index] = child;
        System.arraycopy(children, index, newChildren, index + 1, children.length - index);
        return new EdgeMapBranch(bitmap | bit, newChildren);
    }

    private @Nullable EdgeMapNode removeChild(int bit, int index) {
        if (children.length == 1) {
            return null;
        }
        EdgeMapNode[] newChildren = new EdgeMapNode[children.length - 1];
        System.arraycopy(children, 0, newChildren, 0, index);
        System.arraycopy(children, index + 1, newChildren, index, children.length - index - 1);
        if (newChildren.length == 1 && newChildren[0] instanceof EdgeMapLeaf) {
            return newChildren[0];
        }
        return new EdgeMapBranch(bitmap ^ bit, newChildren);
    }

    private int index(int bit) {
        return Integer.bitCount(bitmap & (bit - 1));
    }

    private static int slot(int hash, int shift) {
        return (hash >>> shift) & 31;
    }

    private static int bit(int slot) {
        return 1 << slot;
    }
}
