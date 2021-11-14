package graphql.schema.diffing;

import com.google.common.collect.*;
import com.google.common.util.concurrent.AtomicDouble;
import graphql.Assert;
import graphql.collect.ImmutableKit;
import graphql.schema.*;
import graphql.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

public class SchemaDiffing {

    private static class Mapping {
    }

    private static class MappingEntry {
        public MappingEntry(ImmutableList<Vertex> partialMapping, int level, double lowerBoundCost, ImmutableList<Vertex> candidates) {
            this.partialMapping = partialMapping;
            this.level = level;
            this.lowerBoundCost = lowerBoundCost;
            this.candidates = candidates;
        }

        public MappingEntry() {

        }

        // target vertices which the fist `level` vertices of source graph are mapped to
        ImmutableList<Vertex> partialMapping = ImmutableList.<Vertex>builder().build();
        int level;
        double lowerBoundCost;
        ImmutableList<Vertex> candidates = ImmutableList.<Vertex>builder().build();
    }

    public void diff(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) {
        SchemaGraph sourceGraph = new SchemaGraphFactory().createGraph(graphQLSchema1);
        SchemaGraph targetGraph = new SchemaGraphFactory().createGraph(graphQLSchema2);
        // we assert here that the graphs have the same size. The algorithm depends on it
        assertTrue(sourceGraph.size() == targetGraph.size());
        int graphSize = sourceGraph.size();

        AtomicDouble upperBoundCost = new AtomicDouble(Double.MAX_VALUE);
        AtomicReference<List<Vertex>> result = new AtomicReference<>();
        PriorityQueue<MappingEntry> queue = new PriorityQueue<MappingEntry>((mappingEntry1, mappingEntry2) -> {
            int compareResult = Double.compare(mappingEntry1.lowerBoundCost, mappingEntry2.lowerBoundCost);
            if (compareResult == 0) {
                return (-1) * Integer.compare(mappingEntry1.level, mappingEntry2.level);
            } else {
                return compareResult;
            }
        });
        queue.add(new MappingEntry());
        while (!queue.isEmpty()) {
            MappingEntry mappingEntry = queue.poll();
            if (mappingEntry.lowerBoundCost >= upperBoundCost.doubleValue()) {
                continue;
            }
            // generate sibling
            if (mappingEntry.level > 0 && mappingEntry.candidates.size() > 0) {
                // we need to remove the last mapping
                ImmutableList<Vertex> parentMapping = mappingEntry.partialMapping.subList(0, mappingEntry.partialMapping.size() - 1);
                genNextMapping(parentMapping, mappingEntry.level, mappingEntry.candidates, queue, upperBoundCost, result, sourceGraph, targetGraph);
            }
            // generate children
            if (mappingEntry.level < graphSize) {
                // candidates are the vertices in target, of which are not used yet in partialMapping
                List<Vertex> childCandidates = new ArrayList<>(targetGraph.getVertices());
                childCandidates.removeAll(mappingEntry.partialMapping);
                genNextMapping(mappingEntry.partialMapping, mappingEntry.level + 1, ImmutableList.copyOf(childCandidates), queue, upperBoundCost, result, sourceGraph, targetGraph);
            }
        }
        System.out.println("ged cost: " + upperBoundCost.doubleValue());
        System.out.println("mapping : " + result);
    }

    // level starts at 1 indicating the level in the search tree to look for the next mapping
    private void genNextMapping(ImmutableList<Vertex> partialMappingTargetList,
                                int level,
                                ImmutableList<Vertex> candidates,
                                PriorityQueue<MappingEntry> queue,
                                AtomicDouble upperBound,
                                AtomicReference<List<Vertex>> bestMapping,
                                SchemaGraph sourceGraph,
                                SchemaGraph targetGraph) {
        List<Vertex> sourceList = sourceGraph.getVertices();
        List<Vertex> targetList = targetGraph.getVertices();
        ArrayList<Vertex> availableTargetVertices = new ArrayList<>(targetList);
        availableTargetVertices.removeAll(partialMappingTargetList);
        Vertex v_i = sourceList.get(level);
        int costMatrixSize = sourceList.size() - level + 1;
        double[][] costMatrix = new double[costMatrixSize][costMatrixSize];

        List<Vertex> partialMappingSourceList = new ArrayList<>(partialMappingTargetList.subList(0, level - 1));

        // we are skipping the first level -i indeces
        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                if (v == v_i && !candidates.contains(u)) {
                    costMatrix[i - level + 1][j] = Integer.MAX_VALUE;
                } else {
                    costMatrix[i - level + 1][j] = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMappingSourceList, partialMappingTargetList);
                }
                j++;
            }
        }

        // find out the best extension
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);
        int[] assignments = hungarianAlgorithm.execute();
        int v_i_target_Index = assignments[0];
        Vertex bestExtensionTargetVertex = targetList.get(v_i_target_Index);

        double bestExtensionLowerBound = costMatrix[0][v_i_target_Index];
        if (bestExtensionLowerBound < upperBound.doubleValue()) {
            ImmutableList<Vertex> newMapping = ImmutableKit.addToList(partialMappingTargetList, bestExtensionTargetVertex);
            ImmutableList<Vertex> newCandidates = removeVertex(candidates, bestExtensionTargetVertex);
            queue.add(new MappingEntry(newMapping, level, bestExtensionLowerBound, newCandidates));

            // we have a full mapping from the cost matrix
            List<Vertex> fullMapping = new ArrayList<>(partialMappingTargetList);
            for (int i = 0; i < assignments.length; i++) {
                fullMapping.add(availableTargetVertices.get(assignments[i]));
            }
            // the cost of the full mapping is the new upperBound (aka the current best mapping)
            upperBound.set(editorialCostForFullMapping(fullMapping, sourceGraph, targetGraph));
            bestMapping.set(fullMapping);
        }
    }

    // minimum number of edit operations for a full mapping
    private int editorialCostForFullMapping(List<Vertex> fullMapping, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        int cost = 0;
        int i = 0;
        for (Vertex v : sourceGraph.getVertices()) {
            Vertex targetVertex = fullMapping.get(i++);
            // Vertex changing (relabeling)
            boolean equalNodes = v.getType().equals(targetVertex.getType()) && v.getProperties().equals(targetVertex.getProperties());
            if (!equalNodes) {
                cost++;
            }
            List<Edge> edges = sourceGraph.getEdges(v);
            // edge deletion or relabeling
            for (Edge sourceEdge : edges) {
                Vertex targetFrom = getTargetVertex(fullMapping, sourceEdge.getFrom(), sourceGraph);
                Vertex targetTo = getTargetVertex(fullMapping, sourceEdge.getTo(), sourceGraph);
                Edge targetEdge = targetGraph.getEdge(targetFrom, targetTo);
                if (targetEdge == null || !sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                    cost++;
                }
            }

            // edge insertion
            for (Edge targetEdge : targetGraph.getEdges()) {
                Vertex sourceFrom = getSourceVertex(fullMapping, targetEdge.getFrom(), sourceGraph);
                Vertex sourceTo = getSourceVertex(fullMapping, targetEdge.getTo(), sourceGraph);
                if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                    cost++;
                }
            }
        }
        return cost;
    }

    // TODO: very inefficient
    private Vertex getTargetVertex(List<Vertex> mappingTargetVertices, Vertex sourceVertex, SchemaGraph sourceGraph) {
        for (int i = 0; i < sourceGraph.getVertices().size(); i++) {
            Vertex v = sourceGraph.getVertices().get(i);
            if (v != sourceVertex) continue;
            return mappingTargetVertices.get(i);
        }
        return Assert.assertShouldNeverHappen();
    }

    private Vertex getSourceVertex(List<Vertex> mappingTargetVertices, Vertex targetVertex, SchemaGraph sourceGraph) {
        int index = mappingTargetVertices.indexOf(targetVertex);
        assertTrue(index > -1);
        return Assert.assertNotNull(sourceGraph.getVertices().get(index));
    }

    // lower bound mapping cost between for v -> u in respect to a partial mapping
    private double calcLowerBoundMappingCost(Vertex v,
                                             Vertex u,
                                             SchemaGraph sourceGraph,
                                             SchemaGraph targetGraph,
                                             List<Vertex> partialMappingSourceList,
                                             List<Vertex> partialMappingTargetList) {
        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());
        // inner edge labels of u (resp. v) in regards to the partial mapping: all labels of edges
        // which are adjacent of u (resp. v) which are inner edges

        List<Edge> adjacentEdgesV = sourceGraph.getEdges(v);
        Set<Vertex> nonMappedSourceVertices = nonMappedVertices(sourceGraph.getVertices(), partialMappingSourceList);
        Multiset<String> multisetLabelsV = HashMultiset.create();

        for (Edge edge : adjacentEdgesV) {
            if (nonMappedSourceVertices.contains(edge.getFrom()) && nonMappedSourceVertices.contains(edge.getTo())) {
                multisetLabelsV.add(edge.getLabel());
            }
        }
        List<Edge> adjacentEdgesU = targetGraph.getEdges(u);
        Set<Vertex> nonMappedTargetVertices = nonMappedVertices(targetGraph.getVertices(), partialMappingTargetList);
        Multiset<String> multisetLabelsU = HashMultiset.create();
        for (Edge edge : adjacentEdgesU) {
            if (nonMappedTargetVertices.contains(edge.getFrom()) && nonMappedTargetVertices.contains(edge.getTo())) {
                multisetLabelsU.add(edge.getLabel());
            }
        }

        int anchoredVerticesCost = 0;
        for (int i = 0; i < partialMappingSourceList.size(); i++) {
            Vertex vPrime = partialMappingSourceList.get(i);
            Vertex mappedVPrime = partialMappingTargetList.get(i);
            Edge sourceEdge = sourceGraph.getEdge(v, vPrime);
            Edge targetEdge = targetGraph.getEdge(u, mappedVPrime);
            if (sourceEdge != targetEdge) {
                anchoredVerticesCost++;
            }
        }
        Multiset<String> intersection = Multisets.intersection(multisetLabelsV, multisetLabelsU);
        int multiSetEditDistance = Math.max(multisetLabelsV.size(), multisetLabelsU.size()) - intersection.size();

        return (equalNodes ? 0 : 1) + multiSetEditDistance / 2.0 + anchoredVerticesCost;
    }

    private Set<Vertex> nonMappedVertices(List<Vertex> allVertices, List<Vertex> partialMapping) {
        Set<Vertex> set = new LinkedHashSet<>(allVertices);
        partialMapping.forEach(set::remove);
        return set;
    }


    private ImmutableList<Vertex> removeVertex(ImmutableList<Vertex> list, Vertex toRemove) {
        ImmutableList.Builder<Vertex> builder = ImmutableList.builder();
        for (Vertex vertex : list) {
            if (vertex != toRemove) {
                builder.add(vertex);
            }
        }
        return builder.build();
    }

}
