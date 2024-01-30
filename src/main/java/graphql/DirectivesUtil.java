package graphql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.util.FpKit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static java.util.stream.Collectors.toSet;

@Internal
public class DirectivesUtil {

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static Map<String, GraphQLDirective> nonRepeatableDirectivesByName(List<GraphQLDirective> directives) {
        // filter the repeatable directives
        List<GraphQLDirective> singletonDirectives = directives.stream()
                .filter(d -> !d.isRepeatable()).collect(Collectors.toList());

        return FpKit.getByName(singletonDirectives, GraphQLDirective::getName);
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static Map<String, ImmutableList<GraphQLDirective>> allDirectivesByName(List<GraphQLDirective> directives) {

        return ImmutableMap.copyOf(FpKit.groupingBy(directives, GraphQLDirective::getName));
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static Optional<GraphQLArgument> directiveWithArg(List<GraphQLDirective> directives, String directiveName, String argumentName) {
        GraphQLDirective directive = nonRepeatableDirectivesByName(directives).get(directiveName);
        GraphQLArgument argument = null;
        if (directive != null) {
            argument = directive.getArgument(argumentName);
        }
        return Optional.ofNullable(argument);
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static boolean isAllNonRepeatable(List<GraphQLDirective> directives) {
        if (directives == null || directives.isEmpty()) {
            return false;
        }
        for (GraphQLDirective graphQLDirective : directives) {
            if (graphQLDirective.isRepeatable()) {
                return false;
            }
        }
        return true;
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static List<GraphQLDirective> add(List<GraphQLDirective> targetList, GraphQLDirective newDirective) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirective, () -> "directive can't be null");
        targetList.add(newDirective);
        return targetList;
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static List<GraphQLDirective> addAll(List<GraphQLDirective> targetList, List<GraphQLDirective> newDirectives) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirectives, () -> "directive list can't be null");
        targetList.addAll(newDirectives);
        return targetList;
    }

    @Deprecated(since = "2022-02-24") // use GraphQLAppliedDirectives eventually
    public static GraphQLDirective getFirstDirective(String name, Map<String, List<GraphQLDirective>> allDirectivesByName) {
        List<GraphQLDirective> directives = allDirectivesByName.getOrDefault(name, emptyList());
        if (directives.isEmpty()) {
            return null;
        }
        return directives.get(0);
    }

    /**
     * This can take a collection of legacy directives and turn them applied directives, and combine them with any applied directives.  The applied
     * directives collection takes precedence.
     *
     * @param directiveContainer the schema element holding applied directives
     *
     * @return a combined list unique by name
     */
    public static List<GraphQLAppliedDirective> toAppliedDirectives(GraphQLDirectiveContainer directiveContainer) {
        return toAppliedDirectives(directiveContainer.getAppliedDirectives(), directiveContainer.getDirectives());
    }

    /**
     * This can take a collection of legacy directives and turn them applied directives, and combine them with any applied directives.  The applied
     * directives collection takes precedence.
     *
     * @param appliedDirectives the applied directives to use
     * @param directives        the legacy directives to use
     *
     * @return a combined list unique by name
     */
    public static List<GraphQLAppliedDirective> toAppliedDirectives(Collection<GraphQLAppliedDirective> appliedDirectives, Collection<GraphQLDirective> directives) {
        Set<String> named = appliedDirectives.stream()
                .map(GraphQLAppliedDirective::getName).collect(toSet());

        ImmutableList.Builder<GraphQLAppliedDirective> list = ImmutableList.<GraphQLAppliedDirective>builder()
                .addAll(appliedDirectives);
        // we only put in legacy directives if the list does not already contain them.  We need this mechanism
        // (and not a map) because of repeated directives
        directives.forEach(directive -> {
            if (!named.contains(directive.getName())) {
                list.add(directive.toAppliedDirective());
            }
        });
        return list.build();
    }

    /**
     * A holder class that breaks a list of directives into maps to be more easily accessible in using classes
     */
    public static class DirectivesHolder {

        private final ImmutableMap<String, List<GraphQLDirective>> allDirectivesByName;
        private final ImmutableMap<String, GraphQLDirective> nonRepeatableDirectivesByName;
        private final List<GraphQLDirective> allDirectives;

        private final ImmutableMap<String, List<GraphQLAppliedDirective>> allAppliedDirectivesByName;
        private final List<GraphQLAppliedDirective> allAppliedDirectives;

        public DirectivesHolder(Collection<GraphQLDirective> allDirectives, Collection<GraphQLAppliedDirective> allAppliedDirectives) {
            this.allDirectives = ImmutableList.copyOf(allDirectives);
            this.allDirectivesByName = ImmutableMap.copyOf(FpKit.groupingBy(allDirectives, GraphQLDirective::getName));
            // filter out the repeatable directives
            List<GraphQLDirective> nonRepeatableDirectives = allDirectives.stream()
                    .filter(d -> !d.isRepeatable()).collect(Collectors.toList());
            this.nonRepeatableDirectivesByName = ImmutableMap.copyOf(FpKit.getByName(nonRepeatableDirectives, GraphQLDirective::getName));

            this.allAppliedDirectives = ImmutableList.copyOf(allAppliedDirectives);
            this.allAppliedDirectivesByName = ImmutableMap.copyOf(FpKit.groupingBy(allAppliedDirectives, GraphQLAppliedDirective::getName));

        }

        public ImmutableMap<String, List<GraphQLDirective>> getAllDirectivesByName() {
            return allDirectivesByName;
        }

        public ImmutableMap<String, GraphQLDirective> getDirectivesByName() {
            return nonRepeatableDirectivesByName;
        }

        public List<GraphQLDirective> getDirectives() {
            return allDirectives;
        }

        public GraphQLDirective getDirective(String directiveName) {
            List<GraphQLDirective> directiveList = allDirectivesByName.get(directiveName);
            if (directiveList == null || directiveList.isEmpty()) {
                return null;
            }
            return directiveList.get(0);
        }

        public List<GraphQLDirective> getDirectives(String directiveName) {
            return allDirectivesByName.getOrDefault(directiveName, emptyList());
        }

        public ImmutableMap<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
            return allAppliedDirectivesByName;
        }

        public List<GraphQLAppliedDirective> getAppliedDirectives() {
            return allAppliedDirectives;
        }

        public List<GraphQLAppliedDirective> getAppliedDirectives(String directiveName) {
            return allAppliedDirectivesByName.getOrDefault(directiveName, emptyList());
        }

        public GraphQLAppliedDirective getAppliedDirective(String directiveName) {
            List<GraphQLAppliedDirective> list = allAppliedDirectivesByName.getOrDefault(directiveName, emptyList());
            return list.isEmpty() ? null : list.get(0);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", DirectivesHolder.class.getSimpleName() + "[", "]")
                    .add("allDirectivesByName=" + String.join(",", allDirectivesByName.keySet()))
                    .add("allAppliedDirectivesByName=" + String.join(",", allAppliedDirectivesByName.keySet()))
                    .toString();
        }
    }
}
