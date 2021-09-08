package graphql.schema.idl;

import com.google.common.collect.ImmutableMap;
import graphql.language.SDLDefinition;
import graphql.language.SDLNamedDefinition;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchemaElement;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * This class will track what order {@link SDLDefinition} were parsed in
 * via {@link SchemaParser} and {@link TypeDefinitionRegistry}
 */
public class SchemaParseOrder implements Serializable {

    private final Map<String, List<SDLDefinition<?>>> definitionOrder = new LinkedHashMap<>();


    /**
     * This map is the order in which {@link SDLDefinition}s were parsed per unique {@link SourceLocation#getSourceName()}.  If there
     * is no source then the empty string "" is used.
     *
     * @return a map of source names to definitions in parsed order
     */
    public Map<String, List<SDLDefinition<?>>> getInOrder() {
        return ImmutableMap.copyOf(definitionOrder);
    }

    /**
     * This map is the order in which {@link SDLDefinition}s were parsed per unique {@link SourceLocation#getSourceName()} and it
     * only contains {@link SDLNamedDefinition}s.  If there is no source then the empty string "" is used.
     *
     * @return a map of source names to definitions in parsed order
     */
    public Map<String, List<SDLNamedDefinition<?>>> getInNameOrder() {
        Map<String, List<SDLNamedDefinition<?>>> named = new LinkedHashMap<>();
        definitionOrder.forEach((location, def) -> {
            List<SDLNamedDefinition<?>> namedDefs = def.stream()
                    .filter(d -> d instanceof SDLNamedDefinition)
                    .map(d -> (SDLNamedDefinition<?>) d)
                    .collect(Collectors.toList());
            named.put(location, namedDefs);
        });
        return named;
    }

    /**
     * This comparator will sort according to the original parsed order
     *
     * @param <T> is a {@link GraphQLSchemaElement}
     *
     * @return a comparator that sorts schema elements in parsed order
     */
    public <T extends GraphQLSchemaElement> Comparator<? super T> getElementComparator() {
        // relies in the fact that names in graphql schema are unique across ALL elements
        Map<String, Integer> namedSortValues = buildNameIndex(getInNameOrder());
        return (e1, e2) -> {
            if (isAssignable(e1, GraphQLFieldDefinition.class, GraphQLInputObjectField.class, GraphQLEnumValueDefinition.class) ||
                    isAssignable(e2, GraphQLFieldDefinition.class, GraphQLInputObjectField.class, GraphQLEnumValueDefinition.class)
            ) {
                return 0; // as is - they are not parsed tracked
            }
            if (isAssignable(e1, GraphQLDirective.class, GraphQLNamedType.class) && isAssignable(e2, GraphQLDirective.class, GraphQLNamedType.class)) {
                int sortVal1 = sortValue((GraphQLNamedSchemaElement) e1, namedSortValues);
                int sortVal2 = sortValue((GraphQLNamedSchemaElement) e2, namedSortValues);
                return Integer.compare(sortVal1, sortVal2);
            }
            return -1; // sort to the top
        };
    }

    private <T extends GraphQLSchemaElement> boolean isAssignable(T e1, Class<?>... classes) {
        return Arrays.stream(classes).anyMatch(aClass -> aClass.isAssignableFrom(e1.getClass()));
    }

    private Integer sortValue(GraphQLNamedSchemaElement e1, Map<String, Integer> namedSortValues) {
        return namedSortValues.getOrDefault(e1.getName(), -1);
    }

    private Map<String, Integer> buildNameIndex(Map<String, List<SDLNamedDefinition<?>>> inNameOrder) {
        Map<String, Integer> nameIndex = new HashMap<>();
        int sourceIndex = 0;
        for (Map.Entry<String, List<SDLNamedDefinition<?>>> entry : inNameOrder.entrySet()) {
            List<SDLNamedDefinition<?>> namedDefs = entry.getValue();
            for (int i = 0; i < namedDefs.size(); i++) {
                SDLNamedDefinition<?> namedDefinition = namedDefs.get(i);
                // we will put it in parsed order AND with first sources first
                int index = i + (sourceIndex * 100_000_000);
                nameIndex.put(namedDefinition.getName(), index);
            }
            sourceIndex++;
        }
        return nameIndex;
    }

    /**
     * This adds a new {@link SDLDefinition} to the order
     *
     * @param sdlDefinition the SDL definition to add
     * @param <T>           for two
     *
     * @return this {@link SchemaParseOrder} for fluent building
     */
    public <T extends SDLDefinition<?>> SchemaParseOrder addDefinition(T sdlDefinition) {
        if (sdlDefinition != null) {
            definitionList(sdlDefinition).add(sdlDefinition);
        }
        return this;
    }

    /**
     * This removes a {@link SDLDefinition} from the order
     *
     * @param sdlDefinition the SDL definition to remove
     * @param <T>           for two
     *
     * @return this {@link SchemaParseOrder} for fluent building
     */
    public <T extends SDLDefinition<?>> SchemaParseOrder removeDefinition(T sdlDefinition) {
        definitionList(sdlDefinition).remove(sdlDefinition);
        return this;
    }

    private <T extends SDLDefinition<?>> List<SDLDefinition<?>> definitionList(T sdlDefinition) {
        String location = ofNullable(sdlDefinition.getSourceLocation())
                .map(SourceLocation::getSourceName).orElse("");
        return computeIfAbsent(location);
    }

    private List<SDLDefinition<?>> computeIfAbsent(String location) {
        return definitionOrder.computeIfAbsent(location, k -> new ArrayList<>());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SchemaParseOrder.class.getSimpleName() + "[", "]")
                .add("definitionOrder=" + definitionOrder)
                .toString();
    }
}
