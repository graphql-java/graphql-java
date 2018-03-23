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

    public static <T> BinaryOperator<T> mergeFirst() {
        return (o1, o2) -> o1;
    }

    /**
     * Converts an object that should be an Iterable into a Collection efficiently, leaving
     * it alone if it is already is one.  Useful when you want to get the size of something
     *
     * @param iterableResult the result object
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
}
