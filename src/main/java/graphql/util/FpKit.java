package graphql.util;


import graphql.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    //
    // quickly turn a map of values into its list equivalent
    public static <T> List<T> valuesToList(Map<?, T> map) {
        return new ArrayList<>(map.values());
    }

}
