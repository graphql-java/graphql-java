package graphql.schema;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Taken from https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
@Internal
public class StrongConnect {

    private int index;
    private final Map<GraphQLSchemaElement, Integer> nodeToIndex = new LinkedHashMap<>();
    private final Map<GraphQLSchemaElement, Integer> nodeToLowLink = new LinkedHashMap<>();
    private final Map<GraphQLSchemaElement, Boolean> nodeToOnStack = new LinkedHashMap<>();
    private final Deque<GraphQLSchemaElement> stack = new ArrayDeque<>();
    private final List<Set<GraphQLSchemaElement>> result = new ArrayList<>();

    private final Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies;
    // this includes type references which means it allows for cycles
    private final Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies;


    public StrongConnect(Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies,
                         Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies) {
        this.reverseDependencies = reverseDependencies;
        this.typeRefReverseDependencies = typeRefReverseDependencies;
    }

    public List<Set<GraphQLSchemaElement>> getStronglyConnectedComponents() {
        index = 0;
        for (GraphQLSchemaElement v : reverseDependencies.keySet()) {
            if (nodeToIndex.get(v) == null) {
                strongConnect(v);
            }
        }
        return result;
    }

    private void strongConnect(GraphQLSchemaElement v) {
        nodeToIndex.put(v, index);
        nodeToLowLink.put(v, index);
        index++;
        stack.push(v);
        nodeToOnStack.put(v, true);

        List<GraphQLSchemaElement> dependencies = reverseDependencies.get(v);
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
        if (v instanceof GraphQLNamedType) {
            String name = ((GraphQLNamedType) v).getName();
            if (typeRefReverseDependencies.containsKey(name)) {
                dependencies = new ArrayList<>(dependencies);
                dependencies.addAll(typeRefReverseDependencies.get(name));
            }
        }
        for (GraphQLSchemaElement w : dependencies) {
            if (nodeToIndex.get(w) == null) {
                strongConnect(w);
                nodeToLowLink.put(v, Math.min(nodeToLowLink.get(v), nodeToLowLink.get(w)));
            } else if (Boolean.TRUE.equals(nodeToOnStack.get(w))) {
                nodeToLowLink.put(v, Math.min(nodeToLowLink.get(v), nodeToIndex.get(w)));
            }
        }
        if (nodeToLowLink.get(v).equals(nodeToIndex.get(v))) {
            Set<GraphQLSchemaElement> newSCC = new LinkedHashSet<>();
            GraphQLSchemaElement w;
            do {
                w = stack.pop();
                nodeToOnStack.put(w, false);
                newSCC.add(w);
            } while (w != v);
            result.add(newSCC);
        }
    }
}
