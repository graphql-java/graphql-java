package graphql.schema.diffing;

import graphql.Internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Internal
public class EditorialCostForMapping {
    /**
     * @see #baseEditorialCostForMapping(Mapping, SchemaGraph, SchemaGraph, List)
     */
    public static int baseEditorialCostForMapping(Mapping mapping, // can be a partial mapping
                                                  SchemaGraph sourceGraph, // the whole graph
                                                  SchemaGraph targetGraph // the whole graph
    ) {
        return baseEditorialCostForMapping(mapping, sourceGraph, targetGraph, new ArrayList<>());
    }

    /**
     * Gets the "editorial cost for mapping" for the base mapping.
     * <p>
     * Use this is as base cost when invoking
     * {@link #editorialCostForMapping(int, Mapping, SchemaGraph, SchemaGraph)}
     * as it heavily speeds up performance.
     */
    public static int baseEditorialCostForMapping(Mapping mapping, // can be a partial mapping
                                                  SchemaGraph sourceGraph, // the whole graph
                                                  SchemaGraph targetGraph, // the whole graph
                                                  List<EditOperation> editOperationsResult) {
        int cost = 0;


        for (int i = 0; i < mapping.size(); i++) {
            Vertex sourceVertex = mapping.getSource(i);
            Vertex targetVertex = mapping.getTarget(i);
            // Vertex changing (relabeling)
            boolean equalNodes = sourceVertex.getType().equals(targetVertex.getType()) && sourceVertex.getProperties().equals(targetVertex.getProperties());
            if (!equalNodes) {
                if (sourceVertex.isIsolated()) {
                    editOperationsResult.add(EditOperation.insertVertex("Insert" + targetVertex, sourceVertex, targetVertex));
                } else if (targetVertex.isIsolated()) {
                    editOperationsResult.add(EditOperation.deleteVertex("Delete " + sourceVertex, sourceVertex, targetVertex));
                } else {
                    editOperationsResult.add(EditOperation.changeVertex("Change " + sourceVertex + " to " + targetVertex, sourceVertex, targetVertex));
                }
                cost++;
            }
        }

        // edge deletion or relabeling
        for (Edge sourceEdge : sourceGraph.getEdges()) {
            // only edges relevant to the subgraph
            if (!mapping.containsSource(sourceEdge.getFrom()) || !mapping.containsSource(sourceEdge.getTo())) {
                continue;
            }
            Vertex targetFrom = mapping.getTarget(sourceEdge.getFrom());
            Vertex targetTo = mapping.getTarget(sourceEdge.getTo());
            Edge targetEdge = targetGraph.getEdge(targetFrom, targetTo);
            if (targetEdge == null) {
                editOperationsResult.add(EditOperation.deleteEdge("Delete edge " + sourceEdge, sourceEdge));
                cost++;
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                editOperationsResult.add(EditOperation.changeEdge("Change " + sourceEdge + " to " + targetEdge, sourceEdge, targetEdge));
                cost++;
            }
        }

        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            if (!mapping.containsTarget(targetEdge.getFrom()) || !mapping.containsTarget(targetEdge.getTo())) {
                continue;
            }
            Vertex sourceFrom = mapping.getSource(targetEdge.getFrom());
            Vertex sourceTo = mapping.getSource(targetEdge.getTo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                editOperationsResult.add(EditOperation.insertEdge("Insert edge " + targetEdge, targetEdge));
                cost++;
            }
        }

        return cost;
    }

    /**
     * Calculates the "editorial cost for mapping" for the non-fixed targets in a {@link Mapping}.
     * <p>
     * The {@code baseCost} argument should be the cost for the fixed mapping from
     * {@link #baseEditorialCostForMapping(Mapping, SchemaGraph, SchemaGraph)}.
     * <p>
     * The sum of the non-fixed costs and the fixed costs is total editorial cost for mapping.
     */
    public static int editorialCostForMapping(int baseCost,
                                              Mapping mapping, // can be a partial mapping
                                              SchemaGraph sourceGraph, // the whole graph
                                              SchemaGraph targetGraph // the whole graph
    ) {
        AtomicInteger cost = new AtomicInteger(baseCost);

        Set<Edge> seenEdges = new LinkedHashSet<>();

        // Tells us whether the edge should be visited. We need to avoid counting edges more than once
        Predicate<Edge> visitEdge = (data) -> {
            if (seenEdges.contains(data)) {
                return false;
            } else {
                seenEdges.add(data);
                return true;
            }
        };

        // Look through
        mapping.forEachNonFixedSourceAndTarget((sourceVertex, targetVertex) -> {
            // Vertex changing (relabeling)
            boolean equalNodes = sourceVertex.getType().equals(targetVertex.getType()) && sourceVertex.getProperties().equals(targetVertex.getProperties());

            if (!equalNodes) {
                cost.getAndIncrement();
            }

            for (Edge sourceEdge : sourceGraph.getAdjacentEdgesAndInverseNonCopy(sourceVertex)) {
                if (!visitEdge.test(sourceEdge)) {
                    continue;
                }

                // only edges relevant to the subgraph
                if (!mapping.containsSource(sourceEdge.getFrom()) || !mapping.containsSource(sourceEdge.getTo())) {
                    continue;
                }

                Vertex targetFrom = mapping.getTarget(sourceEdge.getFrom());
                Vertex targetTo = mapping.getTarget(sourceEdge.getTo());
                Edge targetEdge = targetGraph.getEdge(targetFrom, targetTo);

                if (targetEdge == null) {
                    cost.getAndIncrement();
                } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                    cost.getAndIncrement();
                }
            }

            for (Edge targetEdge : targetGraph.getAdjacentEdgesAndInverseNonCopy(targetVertex)) {
                if (!visitEdge.test(targetEdge)) {
                    continue;
                }

                // only edges relevant to the subgraph
                if (!mapping.containsTarget(targetEdge.getFrom()) || !mapping.containsTarget(targetEdge.getTo())) {
                    continue;
                }

                Vertex sourceFrom = mapping.getSource(targetEdge.getFrom());
                Vertex sourceTo = mapping.getSource(targetEdge.getTo());
                Edge sourceEdge = sourceGraph.getEdge(sourceFrom, sourceTo);

                if (sourceEdge == null) {
                    cost.getAndIncrement();
                }
            }
        });

        return cost.get();
    }
}
