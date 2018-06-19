package graphql.util;


import graphql.Internal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;

@Internal
public class FpKit {

    //
    // From a list of named things, get a map of them by name, merging them according to the merge function
    public static <T> Map<String, T> getByName(List<T> namedObjects, Function<T, String> nameFn, BinaryOperator<T> mergeFunc) {
        return namedObjects.stream().collect(Collectors.toMap(
                nameFn,
                identity(),
                mergeFunc)
        );
    }

    //
    // From a list of named things, get a map of them by name, merging them first one added
    public static <T> Map<String, T> getByName(List<T> namedObjects, Function<T, String> nameFn) {
        return getByName(namedObjects, nameFn, mergeFirst());
    }

    public static <T> BinaryOperator<T> mergeFirst() {
        return (o1, o2) -> o1;
    }

    /**
     * Converts an object that should be an Iterable into a Collection efficiently, leaving
     * it alone if it is already is one.  Useful when you want to get the size of something
     *
     * @param iterableResult the result object
     * @param <T>            the type of thing
     *
     * @return an Iterable from that object
     *
     * @throws java.lang.ClassCastException if its not an Iterable
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> toCollection(Object iterableResult) {
        if (iterableResult.getClass().isArray()) {
            List<Object> collect = IntStream.range(0, Array.getLength(iterableResult))
                    .mapToObj(i -> Array.get(iterableResult, i))
                    .collect(Collectors.toList());
            return (List<T>) collect;
        }
        if (iterableResult instanceof Collection) {
            return (Collection<T>) iterableResult;
        }
        Iterable<T> iterable = (Iterable<T>) iterableResult;
        Iterator<T> iterator = iterable.iterator();
        List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Concatenates (appends) a single elements to an existing list
     *
     * @param l   the list onto which to append the element
     * @param t   the element to append
     * @param <T> the type of elements of the list
     *
     * @return a <strong>new</strong> list componsed of the first list elements and the new element
     */
    public static <T> List<T> concat(List<T> l, T t) {
        return concat(l, singletonList(t));
    }

    /**
     * Concatenates two lists into one
     *
     * @param l1  the first list to concatenate
     * @param l2  the second list to concatenate
     * @param <T> the type of element of the lists
     *
     * @return a <strong>new</strong> list composed of the two concatenated lists elements
     */
    public static <T> List<T> concat(List<T> l1, List<T> l2) {
        ArrayList<T> l = new ArrayList<>(l1);
        l.addAll(l2);
        l.trimToSize();
        return l;
    }

    //
    // quickly turn a map of values into its list equivalent
    public static <T> List<T> valuesToList(Map<?, T> map) {
        return new ArrayList<>(map.values());
    }

}
