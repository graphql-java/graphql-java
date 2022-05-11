package graphql.schema.impl;

import graphql.Assert;
import graphql.Internal;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchemaElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class returns a list of strongly connected components (SCC) which are topologically sorted.
 * The algorithm is from  https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 *
 * The elements inside a SCC are additionally sorted top. itself: normally this is not possible,
 * but we are using for this "inner sort" only the "reverseDependencies" Map which is made out of
 * dependencies based one the Java references between Schema elements, which can't form a cycle.
 *
 * The inner sort algorithm is from https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
 */
@Internal
public class StronglyConnectedComponentsTopologicallySorted {

    private int index;
    private final Map<GraphQLSchemaElement, Integer> nodeToIndex = new LinkedHashMap<>();
    private final Map<GraphQLSchemaElement, Integer> nodeToLowLink = new LinkedHashMap<>();
    private final Map<GraphQLSchemaElement, Boolean> nodeToOnStack = new LinkedHashMap<>();
    private final Deque<GraphQLSchemaElement> stack = new ArrayDeque<>();
    private final List<List<GraphQLSchemaElement>> result = new ArrayList<>();

    private final Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies;
    // this includes type references which means it allows for cycles
    private final Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies;


    private StronglyConnectedComponentsTopologicallySorted(Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies,
                                                           Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies) {
        this.reverseDependencies = reverseDependencies;
        this.typeRefReverseDependencies = typeRefReverseDependencies;
    }

    public static List<List<GraphQLSchemaElement>> getStronglyConnectedComponentsTopologicallySorted(
            Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> reverseDependencies,
            Map<String, List<GraphQLSchemaElement>> typeRefReverseDependencies
    ) {
        StronglyConnectedComponentsTopologicallySorted sccTopSort = new StronglyConnectedComponentsTopologicallySorted(reverseDependencies, typeRefReverseDependencies);
        sccTopSort.calculate();
        return sccTopSort.result;
    }

    private void calculate() {
        index = 0;
        for (GraphQLSchemaElement v : reverseDependencies.keySet()) {
            if (nodeToIndex.get(v) == null) {
                stronglyConnect(v);
            }
        }
    }

    private void stronglyConnect(GraphQLSchemaElement v) {
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
                stronglyConnect(w);
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
            result.add(topologicallySort(newSCC));
        }
    }

    private List<GraphQLSchemaElement> topologicallySort(Set<GraphQLSchemaElement> allNodes) {
        List<GraphQLSchemaElement> result = new ArrayList<>();
        Set<GraphQLSchemaElement> notPermMarked = new LinkedHashSet<>(allNodes);
        Set<GraphQLSchemaElement> tempMarked = new LinkedHashSet<>();
        Set<GraphQLSchemaElement> permMarked = new LinkedHashSet<>();
        /*
         * Taken from: https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
         * while exists nodes without a permanent mark do
         *     select an unmarked node n
         *     visit(n)
         */
        while (true) {
            Iterator<GraphQLSchemaElement> iterator = notPermMarked.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            GraphQLSchemaElement n = iterator.next();
            iterator.remove();
            visit(n, tempMarked, permMarked, notPermMarked, result, allNodes);
        }
        return result;
    }

    private void visit(GraphQLSchemaElement n,
                       Set<GraphQLSchemaElement> tempMarked,
                       Set<GraphQLSchemaElement> permMarked,
                       Set<GraphQLSchemaElement> notPermMarked,
                       List<GraphQLSchemaElement> result,
                       Set<GraphQLSchemaElement> allNodes) {
        /*
         * Taken from: https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search
         * if n has a permanent mark then
         *         return
         *     if n has a temporary mark then
         *         stop   (not a DAG)
         *
         *     mark n with a temporary mark
         *
         *     for each node m with an edge from n to m do
         *         visit(m)
         *
         *     remove temporary mark from n
         *     mark n with a permanent mark
         *     add n to head of L
         */
        if (permMarked.contains(n)) {
            return;
        }
        if (tempMarked.contains(n)) {
            // https://en.wikipedia.org/wiki/Directed_acyclic_graph
            Assert.assertShouldNeverHappen("This schema is not forming an Directed Acyclic Graph : %s has already has a temporary mark", n);
            return;
        }
        tempMarked.add(n);
        if (reverseDependencies.containsKey(n)) {
            for (GraphQLSchemaElement m : reverseDependencies.get(n)) {
                if (allNodes.contains(m)) {
                    visit(m, tempMarked, permMarked, notPermMarked, result, allNodes);
                }
            }
        }
        tempMarked.remove(n);
        permMarked.add(n);
        notPermMarked.remove(n);
        result.add(n);
    }

}
