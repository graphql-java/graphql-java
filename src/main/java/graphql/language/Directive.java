package graphql.language;


import graphql.Internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

public class Directive extends AbstractNode {
    private final String name;
    private final List<Argument> arguments = new ArrayList<>();

    public Directive(String name) {
        this(name, Collections.emptyList());
    }

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments.addAll(arguments);
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public Map<String, Argument> getArgumentsMap() {
        // the spec says that args MUST be unique within context
        return toNameMap(arguments, Argument::getName);
    }

    public Argument getArgument(String argumentName) {
        return getArgumentsMap().get(argumentName);
    }

    public String getName() {
        return name;
    }

    /*
     * A common helper to turn a list of directives into a map according to spec
     */
    @Internal
    public static Map<String, Directive> getDirectivesMap(List<Directive> directives) {
        return toNameMap(directives, Directive::getName);
    }

    private static <T> Map<String, T> toNameMap(List<T> namedObjects, Function<T, String> keyFn) {
        return namedObjects.stream().collect(Collectors.toMap(
                keyFn,
                identity(),
                mergeFirst())
        );
    }

    private static <T> BinaryOperator<T> mergeFirst() {
        return (o1, o2) -> o1;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(arguments);
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Directive directive = (Directive) o;

        return !(name != null ? !name.equals(directive.name) : directive.name != null);

    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
