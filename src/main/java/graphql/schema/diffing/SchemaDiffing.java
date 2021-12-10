package graphql.schema.diffing;

import com.google.common.collect.*;
import com.google.common.util.concurrent.AtomicDouble;
import graphql.schema.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

public class SchemaDiffing {

    private static class MappingEntry {

        public MappingEntry(Mapping partialMapping, int level, double lowerBoundCost, Set<Vertex> availableSiblings) {
            this.partialMapping = partialMapping;
            this.level = level;
            this.lowerBoundCost = lowerBoundCost;
            this.availableSiblings = availableSiblings;
        }

        public MappingEntry() {

        }

        // target vertices which the fist `level` vertices of source graph are mapped to
        Mapping partialMapping = new Mapping();
        int level;
        double lowerBoundCost;
        Set<Vertex> availableSiblings = new LinkedHashSet<>();
    }

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) {
        sourceGraph = new SchemaGraphFactory().createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory().createGraph(graphQLSchema2);
//        System.out.println(GraphPrinter.print(sourceGraph));
        return diffImpl(sourceGraph, targetGraph);
    }

    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        // we assert here that the graphs have the same size. The algorithm depends on it
        if (sourceGraph.size() < targetGraph.size()) {
            sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size());
        } else if (sourceGraph.size() > targetGraph.size()) {
            targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size());
        }
        assertTrue(sourceGraph.size() == targetGraph.size());
        int graphSize = sourceGraph.size();
        System.out.println("graph size: " + graphSize);
        sortSourceGraph(sourceGraph, targetGraph);

        AtomicDouble upperBoundCost = new AtomicDouble(Double.MAX_VALUE);
        AtomicReference<Mapping> bestFullMapping = new AtomicReference<>();
        AtomicReference<List<EditOperation>> bestEdit = new AtomicReference<>();

        PriorityQueue<MappingEntry> queue = new PriorityQueue<MappingEntry>((mappingEntry1, mappingEntry2) -> {
            int compareResult = Double.compare(mappingEntry1.lowerBoundCost, mappingEntry2.lowerBoundCost);
            if (compareResult == 0) {
                return (-1) * Integer.compare(mappingEntry1.level, mappingEntry2.level);
            } else {
                return compareResult;
            }
        });
        queue.add(new MappingEntry());
        int counter = 0;
        while (!queue.isEmpty()) {
            MappingEntry mappingEntry = queue.poll();
            System.out.println("entry at level " + mappingEntry.level + " counter:" + (++counter) + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost);
            if (mappingEntry.lowerBoundCost >= upperBoundCost.doubleValue()) {
//                System.out.println("skipping!");
                continue;
            }
            // generate sibling
            if (mappingEntry.level > 0 && mappingEntry.availableSiblings.size() > 0) {
                // we need to remove the last mapping
                Mapping parentMapping = mappingEntry.partialMapping.removeLastElement();
                genNextMapping(parentMapping, mappingEntry.level, mappingEntry.availableSiblings, queue, upperBoundCost, bestFullMapping, bestEdit, sourceGraph, targetGraph);
            }
            // generate children
            if (mappingEntry.level < graphSize) {
                // candidates are the vertices in target, of which are not used yet in partialMapping
                Set<Vertex> childCandidates = new LinkedHashSet<>(targetGraph.getVertices());
                childCandidates.removeAll(mappingEntry.partialMapping.getTargets());
                genNextMapping(mappingEntry.partialMapping, mappingEntry.level + 1, childCandidates, queue, upperBoundCost, bestFullMapping, bestEdit, sourceGraph, targetGraph);
            }
        }
//        System.out.println("ged cost: " + upperBoundCost.doubleValue());
//        System.out.println("edit : " + bestEdit);
//        for (EditOperation editOperation : bestEdit.get()) {
//            System.out.println(editOperation);
//        }
        return bestEdit.get();
    }

    private void sortSourceGraph(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Map<Vertex, Integer> vertexWeights = new LinkedHashMap<>();
        Map<Edge, Integer> edgesWeights = new LinkedHashMap<>();
        for (Vertex vertex : sourceGraph.getVertices()) {
            vertexWeights.put(vertex, infrequencyWeightForVertex(vertex, targetGraph));
        }
        for (Edge edge : sourceGraph.getEdges()) {
            edgesWeights.put(edge, infrequencyWeightForEdge(edge, targetGraph));
        }
        // start with the vertex with largest total weight
        List<Vertex> result = new ArrayList<>();
        ArrayList<Vertex> nextCandidates = new ArrayList<>(sourceGraph.getVertices());
        nextCandidates.sort(Comparator.comparingInt(o -> totalWeight(sourceGraph, o, vertexWeights, edgesWeights)));
//        System.out.println("0: " + totalWeight(sourceGraph, nextCandidates.get(0), vertexWeights, edgesWeights));
//        System.out.println("last: " + totalWeight(sourceGraph, nextCandidates.get(nextCandidates.size() - 1), vertexWeights, edgesWeights));
//        // starting with the one with largest totalWeight:
        Vertex curVertex = nextCandidates.get(nextCandidates.size() - 1);
        result.add(curVertex);
        nextCandidates.remove(nextCandidates.size() - 1);

        while (nextCandidates.size() > 0) {
            Vertex nextOne = null;
            int curMaxWeight = Integer.MIN_VALUE;
            int index = 0;
            int nextOneIndex = -1;
            for (Vertex candidate : nextCandidates) {
                int totalWeight = totalWeight(sourceGraph, candidate, allAdjacentEdges(sourceGraph, result, candidate), vertexWeights, edgesWeights);
                if (totalWeight > curMaxWeight) {
                    nextOne = candidate;
                    nextOneIndex = index;
                    curMaxWeight = totalWeight;
                }
                index++;
            }
            result.add(nextOne);
            nextCandidates.remove(nextOneIndex);
        }
        System.out.println(result);
//        System.out.println(nextCandidates);
//        Collections.reverse(result);
//        sourceGraph.setVertices(result);
    }

    private List<Edge> allAdjacentEdges(SchemaGraph schemaGraph, List<Vertex> fromList, Vertex to) {
        List<Edge> result = new ArrayList<>();
        for (Vertex from : fromList) {
            Edge edge = schemaGraph.getEdge(from, to);
            if (edge == null) {
                continue;
            }
            result.add(edge);
        }
        return result;
    }

    private int totalWeight(SchemaGraph sourceGraph, Vertex vertex, List<Edge> edges, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isArtificialNode()) {
            return Integer.MIN_VALUE + 2;
        }
        return vertexWeights.get(vertex) + edges.stream().mapToInt(edgesWeights::get).sum();
    }

    private int totalWeight(SchemaGraph sourceGraph, Vertex vertex, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
        List<Edge> adjacentEdges = sourceGraph.getAdjacentEdges(vertex);
        return vertexWeights.get(vertex) + adjacentEdges.stream().mapToInt(edgesWeights::get).sum();
    }

    private int infrequencyWeightForVertex(Vertex sourceVertex, SchemaGraph targetGraph) {
        int count = 0;
        for (Vertex targetVertex : targetGraph.getVertices()) {
            if (sourceVertex.isEqualTo(targetVertex)) {
                count++;
            }
        }
        return 1 - count;
    }

    private int infrequencyWeightForEdge(Edge sourceEdge, SchemaGraph targetGraph) {
        int count = 0;
        for (Edge targetEdge : targetGraph.getEdges()) {
            if (sourceEdge.isEqualTo(targetEdge)) {
                count++;
            }
        }
        return 1 - count;
    }

    // level starts at 1 indicating the level in the search tree to look for the next mapping
    private void genNextMapping(Mapping partialMapping,
                                int level,
                                Set<Vertex> candidates, // changed in place on purpose
                                PriorityQueue<MappingEntry> queue,
                                AtomicDouble upperBound,
                                AtomicReference<Mapping> bestMapping,
                                AtomicReference<List<EditOperation>> bestEdit,
                                SchemaGraph sourceGraph,
                                SchemaGraph targetGraph) {
        assertTrue(level - 1 == partialMapping.size());
        List<Vertex> sourceList = sourceGraph.getVertices();
        List<Vertex> targetList = targetGraph.getVertices();
        ArrayList<Vertex> availableTargetVertices = new ArrayList<>(targetList);
        availableTargetVertices.removeAll(partialMapping.getTargets());
        assertTrue(availableTargetVertices.size() + partialMapping.size() == targetList.size());
        // level starts at 1 ... therefore level - 1 is the current one we want to extend
        Vertex v_i = sourceList.get(level - 1);
        int costMatrixSize = sourceList.size() - level + 1;
        double[][] costMatrix = new double[costMatrixSize][costMatrixSize];


        // we are skipping the first level -i indeces
        int costCounter = 0;
        int overallCount = (sourceList.size() - level) * availableTargetVertices.size();
        Set<Vertex> partialMappingSourceSet = new LinkedHashSet<>(partialMapping.getSources());
        Set<Vertex> partialMappingTargetSet = new LinkedHashSet<>(partialMapping.getTargets());

        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                if (v == v_i && !candidates.contains(u)) {
                    costMatrix[i - level + 1][j] = Integer.MAX_VALUE;
                } else {
                    double cost = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet);
                    costMatrix[i - level + 1][j] = cost;
//                    System.out.println("lower bound cost for mapping " + v + " to " + u + " is " + cost + " with index " + (i - level + 1) + " => " + j);
//                    System.out.println("cost counter: " + (costCounter++) + "/" + overallCount + " percentage: " + (costCounter / (float) overallCount));
                }
                j++;
            }
        }
//        System.out.println("finished matrix");
        // find out the best extension
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);
        int[] assignments = hungarianAlgorithm.execute();

        // calculating the lower bound costs for this extension: editorial cost for the partial mapping + value from the cost matrix for v_i
        int editorialCostForMapping = editorialCostForMapping(partialMapping, sourceGraph, targetGraph, new ArrayList<>());
        double costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i][assignments[i]];
        }
        double lowerBoundForPartialMapping = editorialCostForMapping + costMatrixSum;

        if (lowerBoundForPartialMapping < upperBound.doubleValue()) {
            int v_i_target_Index = assignments[0];
            Vertex bestExtensionTargetVertex = availableTargetVertices.get(v_i_target_Index);
            Mapping newMapping = partialMapping.extendMapping(v_i, bestExtensionTargetVertex);
            candidates.remove(bestExtensionTargetVertex);
//            System.out.println("adding new entry " + getDebugMap(newMapping) + "  at level " + level + " with candidates left: " + candidates.size() + " at lower bound: " + lowerBoundForPartialMapping);
            queue.add(new MappingEntry(newMapping, level, lowerBoundForPartialMapping, candidates));

            // we have a full mapping from the cost matrix
            Mapping fullMapping = partialMapping.copy();
            for (int i = 0; i < assignments.length; i++) {
                fullMapping.add(sourceList.get(level - 1 + i), availableTargetVertices.get(assignments[i]));
            }
            assertTrue(fullMapping.size() == sourceGraph.size());
            List<EditOperation> editOperations = new ArrayList<>();
            int costForFullMapping = editorialCostForMapping(fullMapping, sourceGraph, targetGraph, editOperations);
            if (costForFullMapping < upperBound.doubleValue()) {
                upperBound.set(costForFullMapping);
                bestMapping.set(fullMapping);
                bestEdit.set(editOperations);
                System.out.println("setting new best edit with size " + editOperations.size() + " at level " + level);
            } else {
//                System.out.println("to expensive cost for overall mapping " +);
            }
        } else {
            int v_i_target_Index = assignments[0];
            Vertex bestExtensionTargetVertex = availableTargetVertices.get(v_i_target_Index);
            Mapping newMapping = partialMapping.extendMapping(v_i, bestExtensionTargetVertex);
//            System.out.println("not adding new entrie " + getDebugMap(newMapping) + " because " + lowerBoundForPartialMapping + " to high");
        }
    }

    private List<String> getDebugMap(Mapping mapping) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<Vertex, Vertex> entry : mapping.getMap().entrySet()) {
            result.add(entry.getKey().getDebugName() + "->" + entry.getValue().getDebugName());
        }
        return result;
    }

    // minimum number of edit operations for a full mapping
    private int editorialCostForMapping(Mapping partialOrFullMapping, SchemaGraph sourceGraph, SchemaGraph targetGraph, List<EditOperation> editOperationsResult) {
        int cost = 0;
        for (int i = 0; i < partialOrFullMapping.size(); i++) {
            Vertex sourceVertex = partialOrFullMapping.getSource(i);
            Vertex targetVertex = partialOrFullMapping.getTarget(i);
            // Vertex changing (relabeling)
            boolean equalNodes = sourceVertex.getType().equals(targetVertex.getType()) && sourceVertex.getProperties().equals(targetVertex.getProperties());
            if (!equalNodes) {
                if (sourceVertex.isArtificialNode()) {
                    editOperationsResult.add(new EditOperation(EditOperation.Operation.INSERT_VERTEX, "Insert" + targetVertex, targetVertex));
                } else if (targetVertex.isArtificialNode()) {
                    editOperationsResult.add(new EditOperation(EditOperation.Operation.DELETE_VERTEX, "Delete " + sourceVertex, sourceVertex));
                } else {
                    editOperationsResult.add(new EditOperation(EditOperation.Operation.CHANGE_VERTEX, "Change " + sourceVertex + " to " + targetVertex, Arrays.asList(sourceVertex, targetVertex)));
                }
                cost++;
            }
        }
        Set<Vertex> subGraphSource = new LinkedHashSet<>(sourceGraph.getVertices().subList(0, partialOrFullMapping.size()));
        List<Edge> edges = sourceGraph.getEdges();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            // only edges relevant to the subgraph
            if (!subGraphSource.contains(sourceEdge.getOne()) || !subGraphSource.contains(sourceEdge.getTwo())) {
                continue;
            }
            Vertex target1 = partialOrFullMapping.getTarget(sourceEdge.getOne());
            Vertex target2 = partialOrFullMapping.getTarget(sourceEdge.getTwo());
            Edge targetEdge = targetGraph.getEdge(target1, target2);
            if (targetEdge == null) {
                editOperationsResult.add(new EditOperation(EditOperation.Operation.DELETE_EDGE, "Delete edge " + sourceEdge, sourceEdge));
                cost++;
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                editOperationsResult.add(new EditOperation(EditOperation.Operation.CHANGE_EDGE, "Change " + sourceEdge + " to " + targetEdge, Arrays.asList(sourceEdge, targetEdge)));
                cost++;
            }
        }

        // edge insertion
        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            if (!partialOrFullMapping.containsTarget(targetEdge.getOne()) || !partialOrFullMapping.containsTarget(targetEdge.getTwo())) {
                continue;
            }
            Vertex sourceFrom = partialOrFullMapping.getSource(targetEdge.getOne());
            Vertex sourceTo = partialOrFullMapping.getSource(targetEdge.getTwo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                editOperationsResult.add(new EditOperation(EditOperation.Operation.INSERT_EDGE, "Insert edge " + targetEdge, targetEdge));
                cost++;
            }
        }
        return cost;
    }

    // lower bound mapping cost between for v -> u in respect to a partial mapping
    private double calcLowerBoundMappingCost(Vertex v,
                                             Vertex u,
                                             SchemaGraph sourceGraph,
                                             SchemaGraph targetGraph,
                                             List<Vertex> partialMappingSourceList,
                                             Set<Vertex> partialMappingSourceSet,
                                             List<Vertex> partialMappingTargetList,
                                             Set<Vertex> partialMappingTargetSet
    ) {
        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());
        // inner edge labels of u (resp. v) in regards to the partial mapping: all labels of edges
        // which are adjacent of u (resp. v) which are inner edges

        List<Edge> adjacentEdgesV = sourceGraph.getAdjacentEdges(v);
//        Set<Vertex> nonMappedSourceVertices = nonMappedVertices(sourceGraph.getVertices(), partialMappingSourceList);
        Multiset<String> multisetLabelsV = HashMultiset.create();

        for (Edge edge : adjacentEdgesV) {
            // test if this an inner edge: meaning both edges vertices are part of the non mapped vertices
            // or: at least one edge is part of the partial mapping
//            if (nonMappedSourceVertices.contains(edge.getOne()) && nonMappedSourceVertices.contains(edge.getTwo())) {
            if (!partialMappingSourceSet.contains(edge.getOne()) && !partialMappingSourceSet.contains(edge.getTwo())) {
                multisetLabelsV.add(edge.getLabel());
            }
        }

        List<Edge> adjacentEdgesU = targetGraph.getAdjacentEdges(u);
//        Set<Vertex> nonMappedTargetVertices = nonMappedVertices(targetGraph.getVertices(), partialMappingTargetList);
        Multiset<String> multisetLabelsU = HashMultiset.create();
        for (Edge edge : adjacentEdgesU) {
            // test if this is an inner edge
            if (!partialMappingTargetSet.contains(edge.getOne()) && !partialMappingTargetSet.contains(edge.getTwo())) {
                multisetLabelsU.add(edge.getLabel());
            }
        }


        int anchoredVerticesCost = 0;
        for (int i = 0; i < partialMappingSourceList.size(); i++) {
            Vertex vPrime = partialMappingSourceList.get(i);
            Vertex mappedVPrime = partialMappingTargetList.get(i);
            Edge sourceEdge = sourceGraph.getEdge(v, vPrime);
            String labelSourceEdge = sourceEdge != null ? sourceEdge.getLabel() : null;
            Edge targetEdge = targetGraph.getEdge(u, mappedVPrime);
            String labelTargetEdge = targetEdge != null ? targetEdge.getLabel() : null;
            if (!Objects.equals(labelSourceEdge, labelTargetEdge)) {
                anchoredVerticesCost++;
            }
        }

        Multiset<String> intersection = Multisets.intersection(multisetLabelsV, multisetLabelsU);
        int multiSetEditDistance = Math.max(multisetLabelsV.size(), multisetLabelsU.size()) - intersection.size();
//        System.out.println("equalNodes : " + (equalNodes ? 0 : 1) + " editDistance " + (multiSetEditDistance / 2.0) + " anchored cost" + (anchoredVerticesCost));
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
