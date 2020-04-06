package graphql.validation.rules.experimental.overlappingfieldsmerge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A set representation that is well suited to hash and equality comparisons and fast iteration over members
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
class SortedArraySet<T> implements Iterable<T> {

    private ArrayList<T> sortedMembers;

    public SortedArraySet(ArrayList<T> sortedMembers) {
        this.sortedMembers = sortedMembers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SortedArraySet)) {
            return false;
        }
        SortedArraySet<?> that = (SortedArraySet<?>) o;
        return Objects.equals(sortedMembers, that.sortedMembers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sortedMembers);
    }

    @Override
    public Iterator<T> iterator() {
        return sortedMembers.iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        sortedMembers.forEach(action);
    }

    @Override
    public String toString() {
        return sortedMembers.toString();
    }

    //Beware:
    //The comparator wont be used in the final set for equality or removing duplicates, it's only here for sorting.
    //As such it has to be compatible with the standard equality and hashCode implementations.
    static class Builder<T> {

        private ArrayList<T> members = new ArrayList<T>();
        private Comparator<T> comparator;

        Builder(int sizeHint, Comparator<T> comparator) {
            this.members = new ArrayList<>(sizeHint);
            this.comparator = comparator;
        }

        Builder(Comparator<T> comparator) {
            this.members = new ArrayList<>();
            this.comparator = comparator;
        }

        Builder<T> add(T value) {
            members.add(value);
            return this;
        }

        Builder<T> addAll(Collection<T> values) {
            members.addAll(values);
            return this;
        }

        SortedArraySet<T> build() {
            sortAndRemoveDuplicates();
            return new SortedArraySet<>(members);
        }

        private void sortAndRemoveDuplicates() {
            members.sort(comparator);
            int into = 0;
            int from = 0;
            while (from < members.size()) {
                T firstFrom = members.get(from);
                members.set(into, firstFrom);
                into += 1;
                do {
                    from += 1;
                } while (from < members.size() && members.get(from) == firstFrom);
            }
            members.subList(into, members.size()).clear();
        }
    }

    public static <T> Builder<T> newBuilder(int sizeHint, Comparator<T> comparator) {
        return new Builder<T>(sizeHint, comparator);
    }

    public static <T> Builder<T> newBuilder(Comparator<T> comparator) {
        return new Builder<T>(comparator);
    }

}

