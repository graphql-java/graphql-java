package graphql.schema.diffing;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.AtomicDouble;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.SchemaGraphFactory.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraphFactory.APPLIED_DIRECTIVE;
import static graphql.schema.diffing.SchemaGraphFactory.ARGUMENT;
import static graphql.schema.diffing.SchemaGraphFactory.DIRECTIVE;
import static graphql.schema.diffing.SchemaGraphFactory.DUMMY_TYPE_VERTEX;
import static graphql.schema.diffing.SchemaGraphFactory.ENUM;
import static graphql.schema.diffing.SchemaGraphFactory.ENUM_VALUE;
import static graphql.schema.diffing.SchemaGraphFactory.FIELD;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_FIELD;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.INTERFACE;
import static graphql.schema.diffing.SchemaGraphFactory.OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.SCALAR;
import static graphql.schema.diffing.SchemaGraphFactory.UNION;
import static java.lang.String.format;

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

        Mapping partialMapping = new Mapping();
        int level;
        double lowerBoundCost;
    }

    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2, boolean oldVersion) {
        sourceGraph = new SchemaGraphFactory().createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory().createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);

    }

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) {
        return diffGraphQLSchema(graphQLSchema1, graphQLSchema2, false);
    }

    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        if (sourceGraph.size() < targetGraph.size()) {
            sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size());
        } else if (sourceGraph.size() > targetGraph.size()) {
            targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size());
        }
        assertTrue(sourceGraph.size() == targetGraph.size());
        int graphSize = sourceGraph.size();
        System.out.println("graph size: " + graphSize);
//        sortSourceGraph(sourceGraph, targetGraph);
//        if (true) {
//            String print = GraphPrinter.print(sourceGraph);
//            System.out.println(print);
//            return Collections.emptyList();
//        }

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
        List<Vertex> result = new ArrayList<>();
        ArrayList<Vertex> nextCandidates = new ArrayList<>(sourceGraph.getVertices());
        nextCandidates.sort(Comparator.comparingInt(o -> totalWeightWithAdjacentEdges(sourceGraph, o, vertexWeights, edgesWeights)));
//        System.out.println("0: " + totalWeight(sourceGraph, nextCandidates.get(0), vertexWeights, edgesWeights));
//        System.out.println("last: " + totalWeight(sourceGraph, nextCandidates.get(nextCandidates.size() - 1), vertexWeights, edgesWeights));
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
        int counter = 0;
        int blockedCounter = 0;
        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                double cost = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet);
                costMatrix[i - level + 1][j] = cost;
                j++;
                counter++;
                if (cost == Integer.MAX_VALUE) {
                    blockedCounter++;
                } else {
//                    System.out.println("not blocked " + v.getType());
                }
            }
        }
//        System.out.println("counter: " + counter + " vs " + blockedCounter + " perc: " + (blockedCounter / (double) counter));
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);
        int editorialCostForMapping = editorialCostForMapping(partialMapping, sourceGraph, targetGraph, new ArrayList<>());

        // generate all children (which are siblings to each other)
        List<MappingEntry> siblings = new ArrayList<>();
        for (int child = 0; child < availableTargetVertices.size(); child++) {
            int[] assignments = child == 0 ? hungarianAlgorithm.execute() : hungarianAlgorithm.nextChild();
            if (hungarianAlgorithm.costMatrix[0][assignments[0]] == Integer.MAX_VALUE) {
                break;
            }

            double costMatrixSumSibling = getCostMatrixSum(costMatrix, assignments);
            double lowerBoundForPartialMappingSibling = editorialCostForMapping + costMatrixSumSibling;
//            System.out.println("lower bound: " + child + " : " + lowerBoundForPartialMappingSibling);
            int v_i_target_IndexSibling = assignments[0];
            Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
//            System.out.println("adding new mapping " + v_i + " => " + bestExtensionTargetVertexSibling);
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

            Set<Mapping> existingMappings = new LinkedHashSet<>();
            // first child we add to the queue, otherwise save it for later
            if (child == 0) {
//                System.out.println("adding new child entry " + getDebugMap(sibling.partialMapping) + "  at level " + level + " with candidates left: " + sibling.availableTargetVertices.size() + " at lower bound: " + sibling.lowerBoundCost);
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
//            System.out.println("adding new sibling entry " + getDebugMap(sibling.partialMapping) + "  at level " + level + " with candidates left: " + sibling.availableTargetVertices.size() + " at lower bound: " + sibling.lowerBoundCost);

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
//        if (mapping.size() > 0) {
//            result.add(mapping.getSource(mapping.size() - 1).getType() + " -> " + mapping.getTarget(mapping.size() - 1).getType());
//        }
        for (Map.Entry<Vertex, Vertex> entry : mapping.getMap().entrySet()) {
//            if (!entry.getKey().getType().equals(entry.getValue().getType())) {
//                result.add(entry.getKey().getType() + "->" + entry.getValue().getType());
//            }
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

    static Map<String, List<String>> allowedTypeMappings = new LinkedHashMap<>();

    static {
        allowedTypeMappings.put(DUMMY_TYPE_VERTEX, Collections.singletonList(DUMMY_TYPE_VERTEX));
        allowedTypeMappings.put(SCALAR, Collections.singletonList(SCALAR));
        allowedTypeMappings.put(ENUM, Collections.singletonList(ENUM));
        allowedTypeMappings.put(ENUM_VALUE, Collections.singletonList(ENUM_VALUE));
        allowedTypeMappings.put(OBJECT, Arrays.asList(OBJECT));
        allowedTypeMappings.put(INTERFACE, Arrays.asList(INTERFACE));
        allowedTypeMappings.put(FIELD, Collections.singletonList(FIELD));
        allowedTypeMappings.put(ARGUMENT, Collections.singletonList(ARGUMENT));
        allowedTypeMappings.put(INPUT_OBJECT, Collections.singletonList(INPUT_OBJECT));
        allowedTypeMappings.put(INPUT_FIELD, Collections.singletonList(INPUT_FIELD));
        allowedTypeMappings.put(UNION, Collections.singletonList(UNION));
        allowedTypeMappings.put(APPLIED_DIRECTIVE, Collections.singletonList(APPLIED_DIRECTIVE));
        allowedTypeMappings.put(APPLIED_ARGUMENT, Collections.singletonList(APPLIED_ARGUMENT));
        allowedTypeMappings.put(DIRECTIVE, Collections.singletonList(DIRECTIVE));
    }

    private Map<Vertex, Vertex> forcedMatchingCache = new LinkedHashMap<>();
    private Map<Vertex, Vertex> forcedMatchingNegativeCache = new LinkedHashMap<>();

    private boolean isMappingPossible(Vertex v, Vertex u, SchemaGraph sourceGraph, SchemaGraph targetGraph, Set<Vertex> partialMappingTargetSet) {
        Vertex forcedMatch = forcedMatchingCache.get(v);
        if (forcedMatch != null) {
            return forcedMatch == u;
        }
        // deletion and inserting of vertices
        if (u.isArtificialNode() || v.isArtificialNode()) {
            return true;
        }
        List<String> targetTypes = allowedTypeMappings.get(v.getType());
        if (targetTypes == null) {
            return true;
        }
        boolean contains = targetTypes.contains(u.getType());
        if (!contains) {
            return false;
        }
        if (isNamedType(v.getType())) {
            Vertex targetVertex = targetGraph.getType(v.get("name"));
            if (targetVertex != null && Objects.equals(v.getType(), targetVertex.getType())) {
                if (u == targetVertex) {
                    forcedMatchingCache.put(v, targetVertex);
                    forcedMatchingCache.put(targetVertex, v);
                }
                return u == targetVertex;
            }
        }

        if (DUMMY_TYPE_VERTEX.equals(v.getType())) {
            List<Vertex> adjacentVertices = sourceGraph.getAdjacentVertices(v);
            for (Vertex vertex : adjacentVertices) {
                if (vertex.getType().equals(FIELD)) {
                    Vertex matchingTargetField = findMatchingTargetField(vertex, sourceGraph, targetGraph);
                    if (matchingTargetField != null) {
                        Vertex dummyTypeVertex = getDummyTypeVertex(matchingTargetField, targetGraph);
                        forcedMatchingCache.put(v, dummyTypeVertex);
                        forcedMatchingCache.put(dummyTypeVertex, v);
                        return u == dummyTypeVertex;
                    }
                } else if (vertex.getType().equals(INPUT_FIELD)) {
                    Vertex matchingTargetInputField = findMatchingTargetInputField(vertex, sourceGraph, targetGraph);
                    if (matchingTargetInputField != null) {
                        Vertex dummyTypeVertex = getDummyTypeVertex(matchingTargetInputField, targetGraph);
                        forcedMatchingCache.put(v, dummyTypeVertex);
                        forcedMatchingCache.put(dummyTypeVertex, v);
                        return u == dummyTypeVertex;
                    }
                }
            }

        }
        if (INPUT_FIELD.equals(v.getType())) {
            Vertex matchingTargetInputField = findMatchingTargetInputField(v, sourceGraph, targetGraph);
            if (matchingTargetInputField != null) {
                forcedMatchingCache.put(v, matchingTargetInputField);
                forcedMatchingCache.put(matchingTargetInputField, v);
                return u == matchingTargetInputField;
            }
        }
        if (FIELD.equals(v.getType())) {
            Vertex matchingTargetField = findMatchingTargetField(v, sourceGraph, targetGraph);
            if (matchingTargetField != null) {
                forcedMatchingCache.put(v, matchingTargetField);
                forcedMatchingCache.put(matchingTargetField, v);
                return u == matchingTargetField;
            }
        }
        if (ENUM_VALUE.equals(v.getType())) {
            Vertex matchingTargetEnumValue = findMatchingEnumValue(v, sourceGraph, targetGraph);
            if (matchingTargetEnumValue != null) {
                forcedMatchingCache.put(v, matchingTargetEnumValue);
                forcedMatchingCache.put(matchingTargetEnumValue, v);
                return u == matchingTargetEnumValue;
            }
        }
        return true;

    }

    private Vertex getDummyTypeVertex(Vertex vertex, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(vertex, v -> v.getType().equals(DUMMY_TYPE_VERTEX));
        assertTrue(adjacentVertices.size() == 1);
        return adjacentVertices.get(0);
    }

    private Vertex findMatchingEnumValue(Vertex enumValue, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex enumVertex = getEnum(enumValue, sourceGraph);
        Vertex targetEnumWithSameName = targetGraph.getType(enumVertex.get("name"));
        if (targetEnumWithSameName != null) {
            Vertex matchingTarget = getEnumValue(targetEnumWithSameName, enumValue.get("name"), targetGraph);
            return matchingTarget;
        }
        return null;
    }

    private Vertex findMatchingTargetInputField(Vertex inputField, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex sourceInputObject = getInputFieldsObject(inputField, sourceGraph);
        Vertex targetInputObject = targetGraph.getType(sourceInputObject.get("name"));
        if (targetInputObject != null) {
            Vertex matchingInputField = getInputField(targetInputObject, inputField.get("name"), targetGraph);
            return matchingInputField;
        }
        return null;

    }

    private Vertex findMatchingTargetField(Vertex field, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex sourceFieldsContainer = getFieldsContainer(field, sourceGraph);
        Vertex targetFieldsContainerWithSameName = targetGraph.getType(sourceFieldsContainer.get("name"));
        if (targetFieldsContainerWithSameName != null && targetFieldsContainerWithSameName.getType().equals(sourceFieldsContainer.getType())) {
            Vertex mathchingField = getField(targetFieldsContainerWithSameName, field.get("name"), targetGraph);
            return mathchingField;
        }
        return null;
    }

    private Vertex getInputField(Vertex inputObject, String fieldName, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(inputObject, v -> v.getType().equals(INPUT_FIELD) && fieldName.equals(v.get("name")));
        assertTrue(adjacentVertices.size() <= 1);
        return adjacentVertices.size() == 0 ? null : adjacentVertices.get(0);
    }

    private Vertex getField(Vertex fieldsContainer, String fieldName, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(fieldsContainer, v -> v.getType().equals(FIELD) && fieldName.equals(v.get("name")));
        assertTrue(adjacentVertices.size() <= 1);
        return adjacentVertices.size() == 0 ? null : adjacentVertices.get(0);
    }

    private Vertex getEnumValue(Vertex enumVertex, String valueName, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(enumVertex, v -> valueName.equals(v.get("name")));
        assertTrue(adjacentVertices.size() <= 1);
        return adjacentVertices.size() == 0 ? null : adjacentVertices.get(0);
    }

    private Vertex getInputFieldsObject(Vertex inputField, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(inputField, vertex -> vertex.getType().equals(INPUT_OBJECT));
        assertTrue(adjacentVertices.size() == 1, () -> format("No fields container found for %s", inputField));
        return adjacentVertices.get(0);
    }

    private Vertex getFieldsContainer(Vertex field, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(field, vertex -> vertex.getType().equals(OBJECT) || vertex.getType().equals(INTERFACE));
        assertTrue(adjacentVertices.size() == 1, () -> format("No fields container found for %s", field));
        return adjacentVertices.get(0);
    }

    private Vertex getEnum(Vertex enumValue, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(enumValue, vertex -> vertex.getType().equals(ENUM));
        assertTrue(adjacentVertices.size() == 1, () -> format("No enum found for value %s", enumValue));
        return adjacentVertices.get(0);
    }

    private boolean isNamedType(String type) {
        return Arrays.asList(OBJECT, INTERFACE, INPUT_OBJECT, ENUM, UNION, SCALAR).contains(type);
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
        if (!isMappingPossible(v, u, sourceGraph, targetGraph, partialMappingTargetSet)) {
            return Integer.MAX_VALUE;
        }
        if (!isMappingPossible(u, v, targetGraph, sourceGraph, partialMappingSourceSet)) {
            return Integer.MAX_VALUE;
        }
        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());
        // inner edge labels of u (resp. v) in regards to the partial mapping: all labels of edges
        // which are adjacent of u (resp. v) which are inner edges

        List<Edge> adjacentEdgesV = sourceGraph.getAdjacentEdges(v);
        Multiset<String> multisetLabelsV = HashMultiset.create();

        for (Edge edge : adjacentEdgesV) {
            // test if this an inner edge: meaning both edges vertices are part of the non mapped vertices
            // or: at least one edge is part of the partial mapping
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

        /**
         * looking at all edges from x,vPrime and y,mappedVPrime
         */
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
        return (equalNodes ? 0 : 1) + multiSetEditDistance / 2.0 + anchoredVerticesCost;
    }

}
