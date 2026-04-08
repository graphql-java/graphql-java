package graphql.schema.validation;

import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Internal
public class DirectiveCycleDetector {

    private final GraphQLSchema schema;

    public DirectiveCycleDetector(GraphQLSchema schema) {
        this.schema = schema;
    }

    public String findCycle(GraphQLDirective directive) {
        List<String> path = new ArrayList<>();
        path.add(directiveName(directive.getName()));

        Set<String> visited = new LinkedHashSet<>();
        visited.add(directiveName(directive.getName()));

        return findCycleFromDirective(directive, directive.getName(), path, visited);
    }

    private String findCycleFromDirective(GraphQLDirective directive, String startDirectiveName, List<String> path, Set<String> visited) {
        return findCycleFromInputValueDefinitions(directive.getArguments(), startDirectiveName, path, visited);
    }

    private String findCycleFromInputValueDefinitions(List<? extends GraphQLInputValueDefinition> inputValueDefinitions, String startDirectiveName, List<String> path, Set<String> visited) {
        for (GraphQLInputValueDefinition inputValueDefinition : inputValueDefinitions) {
            String cycle = findCycleFromDirectiveContainer(inputValueDefinition, startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }

            cycle = findCycleFromType(inputValueDefinition.getType(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromDirectiveContainer(GraphQLDirectiveContainer directiveContainer, String startDirectiveName, List<String> path, Set<String> visited) {
        for (graphql.schema.GraphQLAppliedDirective directive : DirectivesUtil.toAppliedDirectives(directiveContainer)) {
            String cycle = findCycleFromDirectiveReference(directive.getName(), startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromDirectiveReference(String directiveName, String startDirectiveName, List<String> path, Set<String> visited) {
        if (directiveName.equals(startDirectiveName) && path.size() == 1) {
            return null;
        }

        String displayName = directiveName(directiveName);
        if (directiveName.equals(startDirectiveName)) {
            return canonicalize(addToPath(path, displayName));
        }
        if (visited.contains(displayName)) {
            return null;
        }

        GraphQLDirective directive = schema.getDirective(directiveName);
        if (directive == null) {
            return null;
        }

        return findCycleFromDirective(
                directive,
                startDirectiveName,
                addToPath(path, displayName),
                addToVisited(visited, displayName)
        );
    }

    private String findCycleFromType(GraphQLInputType inputType, String startDirectiveName, List<String> path, Set<String> visited) {
        GraphQLUnmodifiedType unwrappedType = GraphQLTypeUtil.unwrapAll(inputType);
        GraphQLType resolvedType = resolveType(unwrappedType);
        if (resolvedType == null) {
            return null;
        }

        String displayName = ((graphql.schema.GraphQLNamedSchemaElement) resolvedType).getName();
        if (visited.contains(displayName)) {
            return null;
        }

        List<String> nextPath = addToPath(path, displayName);
        Set<String> nextVisited = addToVisited(visited, displayName);

        if (resolvedType instanceof GraphQLScalarType) {
            return findCycleFromDirectiveContainer((GraphQLScalarType) resolvedType, startDirectiveName, nextPath, nextVisited);
        }
        if (resolvedType instanceof GraphQLEnumType) {
            return findCycleFromEnumType((GraphQLEnumType) resolvedType, startDirectiveName, nextPath, nextVisited);
        }
        if (resolvedType instanceof GraphQLInputObjectType) {
            return findCycleFromInputObjectType((GraphQLInputObjectType) resolvedType, startDirectiveName, nextPath, nextVisited);
        }
        return null;
    }

    private String findCycleFromEnumType(GraphQLEnumType enumType, String startDirectiveName, List<String> path, Set<String> visited) {
        String cycle = findCycleFromDirectiveContainer(enumType, startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }

        for (GraphQLEnumValueDefinition enumValueDefinition : enumType.getValues()) {
            cycle = findCycleFromDirectiveContainer(enumValueDefinition, startDirectiveName, path, visited);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String findCycleFromInputObjectType(GraphQLInputObjectType inputObjectType, String startDirectiveName, List<String> path, Set<String> visited) {
        String cycle = findCycleFromDirectiveContainer(inputObjectType, startDirectiveName, path, visited);
        if (cycle != null) {
            return cycle;
        }
        return findCycleFromInputValueDefinitions(inputObjectType.getFieldDefinitions(), startDirectiveName, path, visited);
    }

    private GraphQLType resolveType(GraphQLType type) {
        if (!(type instanceof GraphQLTypeReference)) {
            return type;
        }
        return schema.getType(((GraphQLTypeReference) type).getName());
    }

    private List<String> addToPath(List<String> path, String element) {
        List<String> nextPath = new ArrayList<>(path);
        nextPath.add(element);
        return nextPath;
    }

    private Set<String> addToVisited(Set<String> visited, String element) {
        Set<String> nextVisited = new LinkedHashSet<>(visited);
        nextVisited.add(element);
        return nextVisited;
    }

    private String canonicalize(List<String> cyclePath) {
        List<String> cycle = cyclePath.subList(0, cyclePath.size() - 1);
        String best = null;
        int bestIndex = 0;

        for (int i = 0; i < cycle.size(); i++) {
            String candidate = rotate(cycle, i);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
                bestIndex = i;
            }
        }

        return best + " -> " + cycle.get(bestIndex);
    }

    private String rotate(List<String> cycle, int offset) {
        List<String> rotated = new ArrayList<>(cycle.size());
        for (int i = 0; i < cycle.size(); i++) {
            rotated.add(cycle.get((i + offset) % cycle.size()));
        }
        return String.join(" -> ", rotated);
    }

    private String directiveName(String directiveName) {
        return "@" + directiveName;
    }
}
