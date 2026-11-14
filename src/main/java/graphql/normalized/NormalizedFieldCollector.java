package graphql.normalized;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.conditional.ConditionalNodes;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;

@Internal
public final class NormalizedFieldCollector {
    private final NormalizedDocument document;
    private final ExecutionContext context;
    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public NormalizedFieldCollector(NormalizedDocument document, ExecutionContext context) {
        this.document = document;
        this.context = context;
    }

    public List<List<Field>> collect(List<Field> parentFields, GraphQLObjectType objectType) {
        Set<Integer> roots = new LinkedHashSet<>();
        for (Field parent : parentFields) {
            NormalizedField field = assertNotNull(document.getFields().get(parent),
                    () -> "Lookahead requires the AST used to create the NormalizedDocument");
            if (field.getSelectionSetId() >= 0) {
                roots.add(field.getSelectionSetId());
            }
        }
        Map<String, Set<Field>> groups = new LinkedHashMap<>();
        Set<Integer> visitedFragments = new LinkedHashSet<>();
        for (int root : roots) {
            collectSelectionSet(root, objectType, groups, visitedFragments);
        }
        List<List<Field>> result = new ArrayList<>();
        groups.values().forEach(fields -> result.add(List.copyOf(fields)));
        return List.copyOf(result);
    }

    private void collectSelectionSet(int root, GraphQLObjectType objectType, Map<String, Set<Field>> groups, Set<Integer> visitedFragments) {
        Deque<Iterator<NormalizedSelection>> frames = new ArrayDeque<>();
        frames.push(selections(root));
        while (!frames.isEmpty()) {
            Iterator<NormalizedSelection> frame = frames.peek();
            if (!frame.hasNext()) {
                frames.pop();
                continue;
            }
            collectSelection(frame.next(), objectType, groups, visitedFragments, frames);
        }
    }

    private void collectSelection(NormalizedSelection selection, GraphQLObjectType objectType, Map<String, Set<Field>> groups,
                                  Set<Integer> visitedFragments, Deque<Iterator<NormalizedSelection>> frames) {
        if (!conditionalNodes.shouldInclude(selection.getSource(), context.getCoercedVariables().toMap(), context.getGraphQLSchema(), context.getGraphQLContext())) {
            return;
        }
        if (selection instanceof NormalizedField field) {
            groups.computeIfAbsent(field.getSource().getResultKey(), ignored -> new LinkedHashSet<>()).add(field.getSource());
            return;
        }
        NormalizedSelectionScope scope = (NormalizedSelectionScope) selection;
        if (!matchesType(scope, objectType) || !includeFragment(scope, visitedFragments)) {
            return;
        }
        frames.push(selections(scope.getSelectionSetId()));
    }

    private boolean matchesType(NormalizedSelectionScope scope, GraphQLObjectType objectType) {
        String condition = scope.getTypeCondition();
        if (condition == null || condition.equals(objectType.getName())) {
            return true;
        }
        GraphQLCompositeType type = (GraphQLCompositeType) context.getGraphQLSchema().getType(condition);
        return !(type instanceof GraphQLObjectType) && context.getGraphQLSchema().isPossibleType(type, objectType);
    }

    private boolean includeFragment(NormalizedSelectionScope scope, Set<Integer> visitedFragments) {
        if (scope.getFragmentDefinition() == null) {
            return true;
        }
        if (!conditionalNodes.shouldInclude(scope.getFragmentDefinition(), context.getCoercedVariables().toMap(), context.getGraphQLSchema(), context.getGraphQLContext())) {
            return false;
        }
        return visitedFragments.add(scope.getSelectionSetId());
    }

    private Iterator<NormalizedSelection> selections(int id) {
        return document.getSelectionSets().get(id).getSelections().iterator();
    }
}
