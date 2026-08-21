package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * An immutable, bitmap-compressed 32-way branch in a persistent integer map.
 *
 * @param <V> the stored value type
 */
@Internal
public final class IntMapBranch<V> implements IntMapNode<V> {

    private final int bitmap;
    private final IntMapNode<V>[] children;

    /**
     * Creates a branch from a bitmap and its dense, slot-ordered child array.
     *
     * @param bitmap the occupied logical slots
     * @param children children for the occupied slots
     */
    public IntMapBranch(int bitmap, IntMapNode<V>[] children) {
        this.bitmap = bitmap;
        this.children = children;
    }

    /**
     * Builds the minimum branch path needed to distinguish two leaves.
     *
     * @param first the first leaf
     * @param second the second leaf
     * @param shift the number of hash bits already consumed
     * @param <V> the stored value type
     *
     * @return a branch containing both leaves
     */
    public static <V> IntMapNode<V> merge(
            IntMapLeaf<V> first,
            IntMapLeaf<V> second,
            int shift) {
        int firstSlot = slot(first.getHash(), shift);
        int secondSlot = slot(second.getHash(), shift);
        if (firstSlot == secondSlot) {
            IntMapNode<V> child = merge(first, second, shift + 5);
            return new IntMapBranch<>(bit(firstSlot), childArray(child));
        }
        int combinedBitmap = bit(firstSlot) | bit(secondSlot);
        IntMapNode<V>[] children = firstSlot < secondSlot
                ? childArray(first, second)
                : childArray(second, first);
        return new IntMapBranch<>(combinedBitmap, children);
    }

    @Override
    public @Nullable V get(int key, int hash, int shift) {
        int bit = bit(slot(hash, shift));
        if ((bitmap & bit) == 0) {
            return null;
        }
        return children[index(bit)].get(key, hash, shift + 5);
    }

    @Override
    public IntMapNode<V> put(int key, int hash, V value, int shift) {
        int bit = bit(slot(hash, shift));
        int index = index(bit);
        if ((bitmap & bit) == 0) {
            return insert(bit, index, new IntMapLeaf<>(key, hash, value));
        }
        IntMapNode<V> oldChild = children[index];
        IntMapNode<V> newChild = oldChild.put(key, hash, value, shift + 5);
        if (oldChild == newChild) {
            return this;
        }
        IntMapNode<V>[] newChildren = Arrays.copyOf(children, children.length);
        newChildren[index] = newChild;
        return new IntMapBranch<>(bitmap, newChildren);
    }

    @Override
    public @Nullable IntMapNode<V> remove(int key, int hash, int shift) {
        int bit = bit(slot(hash, shift));
        if ((bitmap & bit) == 0) {
            return this;
        }
        int index = index(bit);
        IntMapNode<V> oldChild = children[index];
        IntMapNode<V> newChild = oldChild.remove(key, hash, shift + 5);
        if (oldChild == newChild) {
            return this;
        }
        if (newChild != null) {
            IntMapNode<V>[] newChildren = Arrays.copyOf(children, children.length);
            newChildren[index] = newChild;
            return new IntMapBranch<>(bitmap, newChildren);
        }
        return removeChild(bit, index);
    }

    @Override
    public void visitEntries(
            Set<IntMapNode<V>> visitedNodes,
            IntMapEntryVisitor<V> visitor) {
        if (!visitedNodes.add(this)) {
            return;
        }
        for (IntMapNode<V> child : children) {
            child.visitEntries(visitedNodes, visitor);
        }
    }

    @Override
    public void forEachEntry(IntMapEntryVisitor<V> visitor) {
        for (IntMapNode<V> child : children) {
            child.forEachEntry(visitor);
        }
    }

    private IntMapNode<V> insert(
            int bit,
            int index,
            IntMapNode<V> child) {
        IntMapNode<V>[] newChildren = Arrays.copyOf(children, children.length + 1);
        System.arraycopy(children, index, newChildren, index + 1, children.length - index);
        newChildren[index] = child;
        return new IntMapBranch<>(bitmap | bit, newChildren);
    }

    private @Nullable IntMapNode<V> removeChild(int bit, int index) {
        if (children.length == 1) {
            return null;
        }
        IntMapNode<V>[] newChildren = Arrays.copyOf(children, children.length - 1);
        System.arraycopy(children, index + 1, newChildren, index, children.length - index - 1);
        if (newChildren.length == 1 && newChildren[0] instanceof IntMapLeaf<?>) {
            return newChildren[0];
        }
        return new IntMapBranch<>(bitmap ^ bit, newChildren);
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

    @SuppressWarnings("unchecked")
    private static <V> IntMapNode<V>[] childArray(IntMapNode<V> child) {
        IntMapNode<V>[] children = (IntMapNode<V>[]) new IntMapNode<?>[1];
        children[0] = child;
        return children;
    }

    @SuppressWarnings("unchecked")
    private static <V> IntMapNode<V>[] childArray(
            IntMapNode<V> first,
            IntMapNode<V> second) {
        IntMapNode<V>[] children = (IntMapNode<V>[]) new IntMapNode<?>[2];
        children[0] = first;
        children[1] = second;
        return children;
    }
}
