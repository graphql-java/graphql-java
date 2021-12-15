package graphql.schema.diffing;

import com.google.common.collect.*;
import com.google.common.util.concurrent.AtomicDouble;
import graphql.schema.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.INTERFACE;
import static graphql.schema.diffing.SchemaGraphFactory.UNION;

public class SchemaDiffing {

    private static class MappingEntry {

        public List<MappingEntry> mappingEntriesSiblings = new ArrayList<>();
        public int[] assignments;
        public ArrayList<Vertex> availableTargetVertices;

        public MappingEntry(Mapping partialMapping, int level, double lowerBoundCost) {
            this.partialMapping = partialMapping;
            this.level = level;
            this.lowerBoundCost = lowerBoundCost;
        }

        public MappingEntry() {

        }

        // target vertices which the fist `level` vertices of source graph are mapped to
        Mapping partialMapping = new Mapping();
        int level;
        double lowerBoundCost;
//        Set<Vertex> candidates = new LinkedHashSet<>();
    }

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2, boolean oldVersion) {
        sourceGraph = new SchemaGraphFactory().createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory().createGraph(graphQLSchema2);
//        System.out.println(GraphPrinter.print(sourceGraph));
        return diffImpl(sourceGraph, targetGraph);

    }

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) {
        return diffGraphQLSchema(graphQLSchema1, graphQLSchema2, false);
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

        PriorityQueue<MappingEntry> queue = new PriorityQueue<>((mappingEntry1, mappingEntry2) -> {
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
//            System.out.println((++counter) + " entry at level " + mappingEntry.level + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost + " map " + getDebugMap(mappingEntry.partialMapping));
            if ((++counter) % 100 == 0) {
                System.out.println((counter) + " entry at level");
            }
            if (mappingEntry.lowerBoundCost >= upperBoundCost.doubleValue()) {
//                System.out.println("skipping!");
                continue;
            }
            if (mappingEntry.level > 0 && mappingEntry.mappingEntriesSiblings.size() > 0) {
                getSibling(
                        mappingEntry.level,
                        queue,
                        upperBoundCost,
                        bestFullMapping,
                        bestEdit,
                        sourceGraph,
                        targetGraph,
                        mappingEntry);
            }
            if (mappingEntry.level < graphSize) {
                generateChildren(mappingEntry,
                        mappingEntry.level + 1,
                        queue,
                        upperBoundCost,
                        bestFullMapping,
                        bestEdit,
                        sourceGraph,
                        targetGraph
                );
            }
        }
        System.out.println("ged cost: " + upperBoundCost.doubleValue());
        System.out.println("edit : " + bestEdit);
        for (EditOperation editOperation : bestEdit.get()) {
            System.out.println(editOperation);
        }
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
        nextCandidates.sort(Comparator.comparingInt(o -> totalWeightWithAdjacentEdges(sourceGraph, o, vertexWeights, edgesWeights)));
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
                int totalWeight = totalWeightWithSomeEdges(sourceGraph, candidate, allAdjacentEdges(sourceGraph, result, candidate), vertexWeights, edgesWeights);
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
        sourceGraph.setVertices(result);
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

    private int totalWeightWithSomeEdges(SchemaGraph sourceGraph, Vertex vertex, List<Edge> edges, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isArtificialNode()) {
            return Integer.MIN_VALUE + 2;
        }
        return vertexWeights.get(vertex) + edges.stream().mapToInt(edgesWeights::get).sum();
    }

    private int totalWeightWithAdjacentEdges(SchemaGraph sourceGraph, Vertex vertex, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isArtificialNode()) {
            return Integer.MIN_VALUE + 2;
        }
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
    private void generateChildren(MappingEntry parentEntry,
                                  int level,
                                  PriorityQueue<MappingEntry> queue,
                                  AtomicDouble upperBound,
                                  AtomicReference<Mapping> bestMapping,
                                  AtomicReference<List<EditOperation>> bestEdit,
                                  SchemaGraph sourceGraph,
                                  SchemaGraph targetGraph
    ) {
        Mapping partialMapping = parentEntry.partialMapping;
        assertTrue(level - 1 == partialMapping.size());
        List<Vertex> sourceList = sourceGraph.getVertices();
        List<Vertex> targetList = targetGraph.getVertices();

        // TODO: iterates over all target vertices
        ArrayList<Vertex> availableTargetVertices = new ArrayList<>(targetList);
        availableTargetVertices.removeAll(partialMapping.getTargets());
        assertTrue(availableTargetVertices.size() + partialMapping.size() == targetList.size());
        // level starts at 1 ... therefore level - 1 is the current one we want to extend
        Vertex v_i = sourceList.get(level - 1);


        int costMatrixSize = sourceList.size() - level + 1;
        double[][] costMatrix = new double[costMatrixSize][costMatrixSize];


        // we are skipping the first level -i indices
        Set<Vertex> partialMappingSourceSet = new LinkedHashSet<>(partialMapping.getSources());
        Set<Vertex> partialMappingTargetSet = new LinkedHashSet<>(partialMapping.getTargets());

        // costMatrix[0] is the row for  v_i
        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                double cost = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet);
                costMatrix[i - level + 1][j] = cost;
                j++;
            }
        }
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);
        int editorialCostForMapping = editorialCostForMapping(partialMapping, sourceGraph, targetGraph, new ArrayList<>());

        // generate all childrens (which are siblings to each other)
        List<MappingEntry> siblings = new ArrayList<>();
        for (int child = 0; child < availableTargetVertices.size(); child++) {
            int[] assignments = child == 0 ? hungarianAlgorithm.execute() : hungarianAlgorithm.nextChild();
            double costMatrixSumSibling = getCostMatrixSum(costMatrix, assignments);
            double lowerBoundForPartialMappingSibling = editorialCostForMapping + costMatrixSumSibling;
//            System.out.println("lower bound: " + child + " : " + lowerBoundForPartialMappingSibling);
            int v_i_target_IndexSibling = assignments[0];
            Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
            Mapping newMappingSibling = partialMapping.extendMapping(v_i, bestExtensionTargetVertexSibling);

            if (lowerBoundForPartialMappingSibling == parentEntry.lowerBoundCost) {
//                System.out.println("same lower Bound: " + v_i + " -> " + bestExtensionTargetVertexSibling);
            }

            if (lowerBoundForPartialMappingSibling >= upperBound.doubleValue()) {
                break;
            }
            MappingEntry sibling = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMappingSibling);
            sibling.mappingEntriesSiblings = siblings;
            sibling.assignments = assignments;
            sibling.availableTargetVertices = availableTargetVertices;

            // first child we add to the queue, otherwise save it for later
            if (child == 0) {
                queue.add(sibling);
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
                    System.out.println("setting new best edit at level " + level + " with size " + editOperations.size() + " at level " + level);
                }
            } else {
                siblings.add(sibling);
            }

        }

    }

    private void getSibling(
            int level,
            PriorityQueue<MappingEntry> queue,
            AtomicDouble upperBoundCost,
            AtomicReference<Mapping> bestFullMapping,
            AtomicReference<List<EditOperation>> bestEdit,
            SchemaGraph sourceGraph,
            SchemaGraph targetGraph,
            MappingEntry mappingEntry) {

        MappingEntry sibling = mappingEntry.mappingEntriesSiblings.get(0);
        if (sibling.lowerBoundCost < upperBoundCost.doubleValue()) {
//            System.out.println("adding new entry " + getDebugMap(sibling.partialMapping) + "  at level " + level + " with candidates left: " + sibling.availableTargetVertices.size() + " at lower bound: " + sibling.lowerBoundCost);

            queue.add(sibling);
            mappingEntry.mappingEntriesSiblings.remove(0);

            List<Vertex> sourceList = sourceGraph.getVertices();
            // we need to start here from the parent mapping, this is why we remove the last element
            Mapping fullMapping = sibling.partialMapping.removeLastElement();
            for (int i = 0; i < sibling.assignments.length; i++) {
                fullMapping.add(sourceList.get(level - 1 + i), sibling.availableTargetVertices.get(sibling.assignments[i]));
            }
            assertTrue(fullMapping.size() == this.sourceGraph.size());
            List<EditOperation> editOperations = new ArrayList<>();
            int costForFullMapping = editorialCostForMapping(fullMapping, this.sourceGraph, this.targetGraph, editOperations);
            if (costForFullMapping < upperBoundCost.doubleValue()) {
                upperBoundCost.set(costForFullMapping);
                bestFullMapping.set(fullMapping);
                bestEdit.set(editOperations);
                System.out.println("setting new best edit at level " + level + " with size " + editOperations.size() + " at level " + level);

            }
        }
    }


    private double getCostMatrixSum(double[][] costMatrix, int[] assignments) {
        double costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i][assignments[i]];
        }
        return costMatrixSum;
    }

    private Vertex findMatchingVertex(Vertex v_i, List<Vertex> availableTargetVertices, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        String viType = v_i.getType();
        HashMultiset<String> viAdjacentEdges = HashMultiset.create(sourceGraph.getAdjacentEdges(v_i).stream().map(edge -> edge.getLabel()).collect(Collectors.toList()));
        if (viType.equals(SchemaGraphFactory.OBJECT) || viType.equals(INTERFACE) || viType.equals(UNION) || viType.equals(INPUT_OBJECT)) {
            for (Vertex targetVertex : availableTargetVertices) {
                if (v_i.isEqualTo(targetVertex)) {
                    // check if edges are the same
                    HashMultiset<String> adjacentEdges = HashMultiset.create(targetGraph.getAdjacentEdges(targetVertex).stream().map(Edge::getLabel).collect(Collectors.toList()));
                    if (viAdjacentEdges.equals(adjacentEdges)) {
                        return targetVertex;
                    }
                }

            }
        }
        return null;
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
        List<Edge> edges = sourceGraph.getEdges();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            // only edges relevant to the subgraph
            if (!partialOrFullMapping.containsSource(sourceEdge.getOne()) || !partialOrFullMapping.containsSource(sourceEdge.getTwo())) {
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

        //TODO: iterates over all edges in the target Graph
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

}
