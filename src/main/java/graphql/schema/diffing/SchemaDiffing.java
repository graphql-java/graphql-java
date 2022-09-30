package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.AtomicDoubleArray;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static graphql.Assert.assertTrue;

public class SchemaDiffing {

    private static MappingEntry LAST_ELEMENT = new MappingEntry();

    private static class MappingEntry {
        public boolean siblingsFinished;
        public LinkedBlockingQueue<MappingEntry> mappingEntriesSiblings;
        public int[] assignments;
        public List<Vertex> availableTargetVertices;

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
    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2, boolean oldVersion) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);

    }

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        return diffGraphQLSchema(graphQLSchema1, graphQLSchema2, false);
    }


    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        int sizeDiff = targetGraph.size() - sourceGraph.size();
        System.out.println("graph diff: " + sizeDiff);
        FillupIsolatedVertices fillupIsolatedVertices = new FillupIsolatedVertices(sourceGraph, targetGraph);
        fillupIsolatedVertices.ensureGraphAreSameSize();
        FillupIsolatedVertices.IsolatedVertices isolatedVertices = fillupIsolatedVertices.isolatedVertices;

        assertTrue(sourceGraph.size() == targetGraph.size());

        int graphSize = sourceGraph.size();
        System.out.println("graph size: " + graphSize);

        if (sizeDiff != 0) {
            sortSourceGraph(sourceGraph, targetGraph, isolatedVertices);
        }

        AtomicDouble upperBoundCost = new AtomicDouble(Double.MAX_VALUE);
        AtomicReference<Mapping> bestFullMapping = new AtomicReference<>();
        AtomicReference<List<EditOperation>> bestEdit = new AtomicReference<>();

        PriorityQueue<MappingEntry> queue = new PriorityQueue<>((mappingEntry1, mappingEntry2) -> {
            int compareResult = Double.compare(mappingEntry1.lowerBoundCost, mappingEntry2.lowerBoundCost);
            if (compareResult == 0) {
                return Integer.compare(mappingEntry2.level, mappingEntry1.level);
            } else {
                return compareResult;
            }
        });
        queue.add(new MappingEntry());
        int counter = 0;
        while (!queue.isEmpty()) {
            MappingEntry mappingEntry = queue.poll();
            System.out.println((++counter) + " check entry at level " + mappingEntry.level + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost + " map " + getDebugMap(mappingEntry.partialMapping));
//            if ((++counter) % 100 == 0) {
//                System.out.println((counter) + " entry at level");
//            }
            if (mappingEntry.lowerBoundCost >= upperBoundCost.doubleValue()) {
                continue;
            }
            if (mappingEntry.level > 0 && !mappingEntry.siblingsFinished) {
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
                        targetGraph,
                        isolatedVertices
                );
            }
        }
        System.out.println("ged cost: " + upperBoundCost.doubleValue());
//        List<String> debugMap = getDebugMap(bestFullMapping.get());
//        for (String debugLine : debugMap) {
//            System.out.println(debugLine);
//        }
//        System.out.println("edit : " + bestEdit);
//        for (EditOperation editOperation : bestEdit.get()) {
//            System.out.println(editOperation);
//        }
        return bestEdit.get();
    }


    private void sortSourceGraph(SchemaGraph sourceGraph, SchemaGraph targetGraph, FillupIsolatedVertices.IsolatedVertices isolatedVertices) {
//        // we sort descending by number of possible target vertices
//        Collections.sort(sourceGraph.getVertices(), (v1, v2) ->
//
//        {
//
//            int v2Count = v2.isBuiltInType() ? -1 : (v2.isIsolated() ? 0 : isolatedVertices.possibleMappings.get(v2).size());
//            int v1Count = v1.isBuiltInType() ? -1 : (v1.isIsolated() ? 0 : isolatedVertices.possibleMappings.get(v1).size());
//            return Integer.compare(v2Count, v1Count);
//        });
//
//        for (Vertex vertex : sourceGraph.getVertices()) {
//            System.out.println("c: " + isolatedVertices.possibleMappings.get(vertex).size() + " v: " + vertex);
//        }

//
//
//        // how often does each source edge (based on the label) appear in target graph
        Map<String, AtomicInteger> targetLabelCount = new LinkedHashMap<>();
        for (Edge targetEdge : targetGraph.getEdges()) {
            targetLabelCount.computeIfAbsent(targetEdge.getLabel(), __ -> new AtomicInteger()).incrementAndGet();
        }
        // how often does each source vertex (based on the data) appear in the target graph
        Map<Vertex.VertexData, AtomicInteger> targetVertexDataCount = new LinkedHashMap<>();
        for (Vertex targetVertex : targetGraph.getVertices()) {
            targetVertexDataCount.computeIfAbsent(targetVertex.toData(), __ -> new AtomicInteger()).incrementAndGet();
        }

        // an infrequency weight is 1 - count in target. Meaning the higher the
        // value, the smaller the count, the less frequent it.
        // Higher Infrequency => more unique is the vertex/label
        Map<Vertex, Integer> vertexInfrequencyWeights = new LinkedHashMap<>();
        Map<Edge, Integer> edgesInfrequencyWeights = new LinkedHashMap<>();
        for (Vertex vertex : sourceGraph.getVertices()) {
            vertexInfrequencyWeights.put(vertex, 1 - targetVertexDataCount.getOrDefault(vertex.toData(), new AtomicInteger()).get());
        }
        for (Edge edge : sourceGraph.getEdges()) {
            edgesInfrequencyWeights.put(edge, 1 - targetLabelCount.getOrDefault(edge.getLabel(), new AtomicInteger()).get());
        }

        /**
         * vertices are sorted by increasing frequency/decreasing infrequency/decreasing uniqueness
         * we start with the most unique/least frequent/most infrequent and add incrementally the next most infrequent.
         */

        //TODO: improve this: this is doing to much: we just want the max infrequent vertex, not all sorted
        ArrayList<Vertex> nextCandidates = new ArrayList<>(sourceGraph.getVertices());
        nextCandidates.sort(Comparator.comparingInt(o -> totalInfrequencyWeightWithAdjacentEdges(sourceGraph, o, vertexInfrequencyWeights, edgesInfrequencyWeights)));

        Vertex curVertex = nextCandidates.get(nextCandidates.size() - 1);
        nextCandidates.remove(nextCandidates.size() - 1);

        List<Vertex> result = new ArrayList<>();
        result.add(curVertex);
        while (nextCandidates.size() > 0) {
            Vertex nextOne = null;
            int curMaxWeight = Integer.MIN_VALUE;
            int index = 0;
            int nextOneIndex = -1;

            // which ones of the candidates has the highest infrequency weight relatively to the current result set of vertices
            for (Vertex candidate : nextCandidates) {
                List<Edge> allAdjacentEdges = sourceGraph.getAllAdjacentEdges( result, candidate);
                int totalWeight = totalInfrequencyWeightWithSomeEdges(candidate, allAdjacentEdges, vertexInfrequencyWeights, edgesInfrequencyWeights);
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
        sourceGraph.setVertices(result);
    }


    private int totalInfrequencyWeightWithSomeEdges(Vertex vertex,
                                                    List<Edge> edges,
                                                    Map<Vertex, Integer> vertexInfrequencyWeights,
                                                    Map<Edge, Integer> edgesInfrequencyWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isIsolated()) {
            return Integer.MIN_VALUE + 2;
        }
        return vertexInfrequencyWeights.get(vertex) + edges.stream().mapToInt(edgesInfrequencyWeights::get).sum();
    }

    private int totalInfrequencyWeightWithAdjacentEdges(SchemaGraph sourceGraph,
                                                        Vertex vertex,
                                                        Map<Vertex, Integer> vertexInfrequencyWeights,
                                                        Map<Edge, Integer> edgesInfrequencyWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isIsolated()) {
            return Integer.MIN_VALUE + 2;
        }
        List<Edge> adjacentEdges = sourceGraph.getAdjacentEdges(vertex);
        return vertexInfrequencyWeights.get(vertex) + adjacentEdges.stream().mapToInt(edgesInfrequencyWeights::get).sum();
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
                                  AtomicReference<Mapping> bestFullMapping,
                                  AtomicReference<List<EditOperation>> bestEdit,
                                  SchemaGraph sourceGraph,
                                  SchemaGraph targetGraph,
                                  FillupIsolatedVertices.IsolatedVertices isolatedInfo

    ) throws Exception {
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
//        double[][] costMatrix = new double[costMatrixSize][costMatrixSize];

        AtomicDoubleArray[] costMatrix = new AtomicDoubleArray[costMatrixSize];
        Arrays.setAll(costMatrix, (index) -> new AtomicDoubleArray(costMatrixSize));
        // costMatrix gets modified by the hungarian algorithm ... therefore we create two of them
        AtomicDoubleArray[] costMatrixCopy = new AtomicDoubleArray[costMatrixSize];
        Arrays.setAll(costMatrixCopy, (index) -> new AtomicDoubleArray(costMatrixSize));
//
        // we are skipping the first level -i indices
        Set<Vertex> partialMappingSourceSet = new LinkedHashSet<>(partialMapping.getSources());
        Set<Vertex> partialMappingTargetSet = new LinkedHashSet<>(partialMapping.getTargets());


        // costMatrix[0] is the row for  v_i
        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                double cost = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet, isolatedInfo);
                costMatrix[i - level + 1].set(j, cost);
                costMatrixCopy[i - level + 1].set(j, cost);
                j++;
            }
        }
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);

        int[] assignments = hungarianAlgorithm.execute();
        int editorialCostForMapping = editorialCostForMapping(partialMapping, sourceGraph, targetGraph, new ArrayList<>());
        double costMatrixSum = getCostMatrixSum(costMatrixCopy, assignments);


        double lowerBoundForPartialMapping = editorialCostForMapping + costMatrixSum;
        int v_i_target_IndexSibling = assignments[0];
        Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
        Mapping newMappingSibling = partialMapping.extendMapping(v_i, bestExtensionTargetVertexSibling);


        if (lowerBoundForPartialMapping >= upperBound.doubleValue()) {
            return;
        }
        MappingEntry newMappingEntry = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMapping);
        LinkedBlockingQueue<MappingEntry> siblings = new LinkedBlockingQueue<>();
//        newMappingEntry.siblingsReady = new AtomicBoolean();
        newMappingEntry.mappingEntriesSiblings = siblings;
        newMappingEntry.assignments = assignments;
        newMappingEntry.availableTargetVertices = availableTargetVertices;

        queue.add(newMappingEntry);
        Mapping fullMapping = partialMapping.copy();
        for (int i = 0; i < assignments.length; i++) {
            fullMapping.add(sourceList.get(level - 1 + i), availableTargetVertices.get(assignments[i]));
        }

        assertTrue(fullMapping.size() == sourceGraph.size());
        List<EditOperation> editOperations = new ArrayList<>();
        int costForFullMapping = editorialCostForMapping(fullMapping, sourceGraph, targetGraph, editOperations);
        if (costForFullMapping < upperBound.doubleValue()) {
            upperBound.set(costForFullMapping);
            bestFullMapping.set(fullMapping);
            bestEdit.set(editOperations);
            System.out.println("setting new best edit at level " + level + " with size " + editOperations.size() + " at level " + level);
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                calculateChildren(
                        availableTargetVertices,
                        hungarianAlgorithm,
                        costMatrixCopy,
                        editorialCostForMapping,
                        partialMapping,
                        v_i,
                        upperBound.get(),
                        level,
                        siblings
                );
            }
        });
    }

    private void calculateChildren(List<Vertex> availableTargetVertices,
                                   HungarianAlgorithm hungarianAlgorithm,
                                   AtomicDoubleArray[] costMatrixCopy,
                                   double editorialCostForMapping,
                                   Mapping partialMapping,
                                   Vertex v_i,
                                   double upperBound,
                                   int level,
                                   LinkedBlockingQueue<MappingEntry> siblings
    ) {
        for (int child = 1; child < availableTargetVertices.size(); child++) {
            int[] assignments = hungarianAlgorithm.nextChild();
            if (hungarianAlgorithm.costMatrix[0].get(assignments[0]) == Integer.MAX_VALUE) {
                break;
            }

            double costMatrixSumSibling = getCostMatrixSum(costMatrixCopy, assignments);
            double lowerBoundForPartialMappingSibling = editorialCostForMapping + costMatrixSumSibling;
            int v_i_target_IndexSibling = assignments[0];
            Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
            Mapping newMappingSibling = partialMapping.extendMapping(v_i, bestExtensionTargetVertexSibling);


            if (lowerBoundForPartialMappingSibling >= upperBound) {
                break;
            }
            MappingEntry sibling = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMappingSibling);
            sibling.mappingEntriesSiblings = siblings;
            sibling.assignments = assignments;
            sibling.availableTargetVertices = availableTargetVertices;

            // first child we add to the queue, otherwise save it for later
//            System.out.println("add child " + child);
            siblings.add(sibling);
        }
        siblings.add(LAST_ELEMENT);

    }

    private void getSibling(
            int level,
            PriorityQueue<MappingEntry> queue,
            AtomicDouble upperBoundCost,
            AtomicReference<Mapping> bestFullMapping,
            AtomicReference<List<EditOperation>> bestEdit,
            SchemaGraph sourceGraph,
            SchemaGraph targetGraph,
            MappingEntry mappingEntry) throws InterruptedException {

        MappingEntry sibling = mappingEntry.mappingEntriesSiblings.take();
        if (sibling == LAST_ELEMENT) {
            mappingEntry.siblingsFinished = true;
            return;
        }
        if (sibling.lowerBoundCost < upperBoundCost.doubleValue()) {
//            System.out.println("adding new sibling entry " + getDebugMap(sibling.partialMapping) + "  at level " + level + " with candidates left: " + sibling.availableTargetVertices.size() + " at lower bound: " + sibling.lowerBoundCost);

            queue.add(sibling);

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
        } else {
//            System.out.println("sibling not good enough");
        }
    }


    private double getCostMatrixSum(AtomicDoubleArray[] costMatrix, int[] assignments) {
        double costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i].get(assignments[i]);
        }
        return costMatrixSum;
    }

    private void logUnmappable(AtomicDoubleArray[] costMatrix, int[] assignments, List<Vertex> sourceList, ArrayList<Vertex> availableTargetVertices, int level) {
        for (int i = 0; i < assignments.length; i++) {
            double value = costMatrix[i].get(assignments[i]);
            if (value >= Integer.MAX_VALUE) {
                System.out.println("i " + i + " can't mapped");
                Vertex v = sourceList.get(i + level - 1);
                Vertex u = availableTargetVertices.get(assignments[i]);
                System.out.println("from " + v + " to " + u);
            }
        }
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
    private int editorialCostForMapping(Mapping partialOrFullMapping,
                                        SchemaGraph sourceGraph,
                                        SchemaGraph targetGraph,
                                        List<EditOperation> editOperationsResult) {
        int cost = 0;
        for (int i = 0; i < partialOrFullMapping.size(); i++) {
            Vertex sourceVertex = partialOrFullMapping.getSource(i);
            Vertex targetVertex = partialOrFullMapping.getTarget(i);
            // Vertex changing (relabeling)
            boolean equalNodes = sourceVertex.getType().equals(targetVertex.getType()) && sourceVertex.getProperties().equals(targetVertex.getProperties());
            if (!equalNodes) {
                if (sourceVertex.isIsolated()) {
                    editOperationsResult.add(new EditOperation(EditOperation.Operation.INSERT_VERTEX, "Insert" + targetVertex, targetVertex));
                } else if (targetVertex.isIsolated()) {
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
    // this is BMa
    private double calcLowerBoundMappingCost(Vertex v,
                                             Vertex u,
                                             SchemaGraph sourceGraph,
                                             SchemaGraph targetGraph,
                                             List<Vertex> partialMappingSourceList,
                                             Set<Vertex> partialMappingSourceSet,
                                             List<Vertex> partialMappingTargetList,
                                             Set<Vertex> partialMappingTargetSet,
                                             FillupIsolatedVertices.IsolatedVertices isolatedInfo

    ) {
        if (!isolatedInfo.mappingPossible(v, u)) {
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

        double result = (equalNodes ? 0 : 1) + multiSetEditDistance / 2.0 + anchoredVerticesCost;
        return result;
    }


}
