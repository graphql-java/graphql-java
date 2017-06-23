package graphql.language;

import graphql.Internal;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

/**
 * Helper class for working with {@link Node}s
 */
@Internal
public class NodeUtil {

    public static Map<String, Directive> directivesByName(List<Directive> directives) {
        return getByName(directives, Directive::getName);
    }

    public static Map<String, Argument> argumentByName(List<Argument> arguments) {
        return getByName(arguments, Argument::getName);
    }

    private static <T> Map<String, T> getByName(List<T> namedObjects, Function<T, String> nameFn) {
        return namedObjects.stream().collect(Collectors.toMap(
                nameFn,
                identity(),
                mergeFirst())
        );
    }

    private static <T> BinaryOperator<T> mergeFirst() {
        return (o1, o2) -> o1;
    }
}
