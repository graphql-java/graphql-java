package graphql.schema.diffing;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.EditorialCostForMapping.baseEditorialCostForMapping;
import static graphql.schema.diffing.EditorialCostForMapping.editorialCostForMapping;

/**
 * This is an algorithm calculating the optimal edit to change the source graph into the target graph.
 * <p>
 * It is based on the following two papers (both papers are from the same authors. The first one is newer, but the older one is more detailed in some aspects)
 * <p>
 * Accelerating Graph Similarity Search via Efficient GED Computation (https://lijunchang.github.io/pdf/2022-ged-tkde.pdf)
 * <p>
 * Efficient Graph Edit Distance Computation and Verification via Anchor-aware Lower Bound Estimation (https://arxiv.org/abs/1709.06810)
 * <p>
 * The algorithm is a modified version of "AStar-BMao".
 * It is adapted to directed graphs as a GraphQL schema is most naturally represented as directed graph (vs the undirected graphs used in the papers).
 */
@Internal
public class DiffImpl {

    private final PossibleMappingsCalculator possibleMappingsCalculator;
    private final SchemaGraph completeSourceGraph;
    private final SchemaGraph completeTargetGraph;
    private final PossibleMappingsCalculator.PossibleMappings possibleMappings;
    private final SchemaDiffingRunningCheck runningCheck;
    private int startLevel;
    private List<Vertex> allSources;

    private static class MappingEntry {
        public LinkedBlockingQueue<MappingEntry> mappingEntriesSiblings = new LinkedBlockingQueue<>();
        public int[] assignments;

        /**
         * These are the available vertices, relative to the parent mapping.
         * Meaning the last mapped element is NOT contained in it.
         */
        public List<Vertex> availableTargetVertices;


        // the editorial cost of the partial mapping is >= lowerBoundCost
        Mapping partialMapping;
        // lowerBoundCost is the minimum cost of all sibling mappings after partialMapping
        // including the partialMapping itself
        int lowerBoundCost;
        int level; // = partialMapping.size

        Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo;
        public Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings;


        public MappingEntry(Mapping partialMapping, int level, int lowerBoundCost) {
            this.partialMapping = partialMapping;
            this.level = level;
            this.lowerBoundCost = lowerBoundCost;
        }

    }

    /**
     * An optimal edit from one graph to another.
     * The mapping maps all vertices from source to target, but
     * not all mappings represent an actual change. This is why there is a separate list
     * of the actual changes.
     */
    public static class OptimalEdit {
        private final SchemaGraph completeSourceGraph;
        private final SchemaGraph completeTargetGraph;

        public Mapping mapping;
        public int ged = Integer.MAX_VALUE;

        public OptimalEdit(
                SchemaGraph completeSourceGraph,
                SchemaGraph completeTargetGraph) {
            this.completeSourceGraph = completeSourceGraph;
            this.completeTargetGraph = completeTargetGraph;
        }

        public OptimalEdit(
                SchemaGraph completeSourceGraph,
                SchemaGraph completeTargetGraph,
                Mapping mapping,
                int ged) {
            this.completeSourceGraph = completeSourceGraph;
            this.completeTargetGraph = completeTargetGraph;
            this.mapping = mapping;
            this.ged = ged;
        }

        public List<EditOperation> getListOfEditOperations() {
            ArrayList<EditOperation> listOfEditOperations = new ArrayList<>();
            assertTrue(baseEditorialCostForMapping(mapping, completeSourceGraph, completeTargetGraph, listOfEditOperations) == ged);
            return listOfEditOperations;
        }
    }

    public DiffImpl(PossibleMappingsCalculator possibleMappingsCalculator, SchemaGraph completeSourceGraph, SchemaGraph completeTargetGraph, PossibleMappingsCalculator.PossibleMappings possibleMappings, SchemaDiffingRunningCheck runningCheck) {
        this.possibleMappingsCalculator = possibleMappingsCalculator;
        this.completeSourceGraph = completeSourceGraph;
        this.completeTargetGraph = completeTargetGraph;
        this.possibleMappings = possibleMappings;
        this.runningCheck = runningCheck;
    }

    OptimalEdit diffImpl(Mapping startMapping, List<Vertex> allSources, List<Vertex> allTargets, AtomicInteger algoIterationCount) throws Exception {
        int graphSize = allSources.size();
        this.startLevel = startMapping.size();
        this.allSources = allSources;
        System.out.println("graphSize: " + graphSize);

        int fixedEditorialCost = baseEditorialCostForMapping(startMapping, completeSourceGraph, completeTargetGraph);
        int level = startMapping.size();

        List<Vertex> allNonFixedTargets = new ArrayList<>(allTargets);
        startMapping.forEachTarget(allNonFixedTargets::remove);

        MappingEntry firstMappingEntry = new MappingEntry(startMapping, level, fixedEditorialCost);
        firstMappingEntry.availableTargetVertices = allNonFixedTargets;

        OptimalEdit optimalEdit = new OptimalEdit(completeSourceGraph, completeTargetGraph);
        PriorityQueue<MappingEntry> queue = new PriorityQueue<>((mappingEntry1, mappingEntry2) -> {
            int compareResult = Double.compare(mappingEntry1.lowerBoundCost, mappingEntry2.lowerBoundCost);
            // we prefer higher levels for equal lower bound costs
            if (compareResult == 0) {
                return Integer.compare(mappingEntry2.level, mappingEntry1.level);
            } else {
                return compareResult;
            }
        });
        queue.add(firstMappingEntry);


        while (!queue.isEmpty()) {
            MappingEntry mappingEntry = queue.poll();
            algoIterationCount.incrementAndGet();
            // keep for debugging
            System.out.println("ged: " + optimalEdit.ged + " lb: " + mappingEntry.lowerBoundCost + " relative level: " + (mappingEntry.level - startMapping.size()) + " queueSize: " + queue.size() + " algoIterationCount: " + algoIterationCount.get());


            if (mappingEntry.lowerBoundCost >= optimalEdit.ged) {
                // once the lowest lowerBoundCost is not lower than the optimal edit, we are done
                break;
            }

//            if (mappingEntry.level > 0 && !mappingEntry.mappingEntriesSiblings.isEmpty()) {
//                addSiblingToQueue(
//                        fixedEditorialCost,
//                        mappingEntry.level,
//                        queue,
//                        optimalEdit,
//                        allSources,
//                        allTargets,
//                        mappingEntry);
//            }
//            if (mappingEntry.level < graphSize) {
                addChildToQueue(
                        fixedEditorialCost,
                        mappingEntry,
                        queue,
                        optimalEdit,
                        allSources,
                        allTargets
                );
            break;
//            }

//            runningCheck.check();
        }

        return optimalEdit;
    }

    private static class SingleMappingAndCondition {
        private final Vertex from;
        private final Vertex to;
        private final InnerEdgesInfo.SetsMapping condition;

        private SingleMappingAndCondition(Vertex from, Vertex vertex, InnerEdgesInfo.SetsMapping condition) {
            this.from = from;
            this.to = vertex;
            this.condition = condition;
        }
    }

    // this calculates all children for the provided parentEntry, but only the first is directly added to the queue
    private void addChildToQueue(int fixedEditorialCost,
                                 MappingEntry parentEntry,
                                 PriorityQueue<MappingEntry> queue,
                                 OptimalEdit optimalEdit,
                                 List<Vertex> allSources,
                                 List<Vertex> allTargets
    ) {
        Mapping parentPartialMapping = parentEntry.partialMapping;
        int parentLevel = parentEntry.level;
        int level = parentLevel + 1;

        assertTrue(parentLevel == parentPartialMapping.size());

        // the available target vertices are the parent queue entry ones plus
        // minus the additional mapped element in parentPartialMapping
        ArrayList<Vertex> availableTargetVertices = new ArrayList<>(parentEntry.availableTargetVertices);
        availableTargetVertices.remove(parentPartialMapping.getTarget(parentLevel - 1));
        assertTrue(availableTargetVertices.size() + parentPartialMapping.size() == allTargets.size());
        Vertex v_i = allSources.get(parentLevel);


        // the cost matrix is for the non mapped vertices
        int costMatrixSize = allSources.size() - parentLevel;

        // costMatrix gets modified by the hungarian algorithm ... therefore we create two of them
        int[][] costMatrixForHungarianAlgo = new int[costMatrixSize][costMatrixSize];
        int[][] costMatrix = new int[costMatrixSize][costMatrixSize];

        Map<Vertex, Integer> isolatedVerticesCache = new LinkedHashMap<>();
        Map<Vertex, Vertex> nonFixedParentRestrictions = possibleMappingsCalculator.getNonFixedParentRestrictions(completeSourceGraph, completeTargetGraph, parentPartialMapping);

        Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo = new LinkedHashMap<>();

        class SetsMappingWithMapping {
            InnerEdgesInfo.SetsMapping setsMapping;
            SingleMapping singleMapping;

            public SetsMappingWithMapping(InnerEdgesInfo.SetsMapping setsMapping, SingleMapping singleMapping) {
                this.setsMapping = setsMapping;
                this.singleMapping = singleMapping;
            }
        }
        // calc lowerbound per single mapping
        Multimap<SingleMapping, SetsMappingWithMapping> singleMappingToRelevantSetsMapping = HashMultimap.create();
        for (int i = parentLevel; i < allSources.size(); i++) {
            Vertex v = allSources.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                int cost = calcLowerBoundMappingCost(v, u, parentPartialMapping, isolatedVerticesCache, nonFixedParentRestrictions, singleMappingToInnerEdgesInfo);

                SingleMapping singleMapping = new SingleMapping(v, u);
                if (singleMappingToInnerEdgesInfo.containsKey(singleMapping)) {
                    InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(singleMapping);
                    for (InnerEdgesInfo.SetsMapping setsMapping : innerEdgesInfo.getMinimumCostConditions()) {
                        for (Vertex source : setsMapping.from) {
                            for (Vertex target : setsMapping.to) {
                                singleMappingToRelevantSetsMapping.put(new SingleMapping(source, target), new SetsMappingWithMapping(setsMapping, singleMapping));
                            }
                        }
                    }
                }
                costMatrixForHungarianAlgo[i - parentLevel][j] = cost;
                costMatrix[i - parentLevel][j] = cost;
                j++;
            }
            runningCheck.check();
        }


        // calc reduced amount by pair of single mapping
        Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings = new LinkedHashMap<>();
        Set<SingleMapping> relevantSingleMappings = new LinkedHashSet<>();
        for (int i = parentLevel; i < allSources.size(); i++) {
            Vertex v = allSources.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                SingleMapping singleMapping = new SingleMapping(v, u);
                if (singleMappingToRelevantSetsMapping.containsKey(singleMapping)) {
                    relevantSingleMappings.add(singleMapping);
                    Collection<SetsMappingWithMapping> relevantSetsMappingWithMapping = singleMappingToRelevantSetsMapping.get(singleMapping);
                    for (SetsMappingWithMapping setsMappingWithMapping : relevantSetsMappingWithMapping) {
                        Set<SingleMapping> singleMappingsPair = Set.of(singleMapping, setsMappingWithMapping.singleMapping);
                        int reducedValue = setsMappingWithMapping.setsMapping.sameLabels ? 2 : 1;
                        reducedValuesByPairOfMappings.put(singleMappingsPair, reducedValue);
                        relevantSingleMappings.add(setsMappingWithMapping.singleMapping);
                    }
                }
                j++;
            }
        }


        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrixForHungarianAlgo);
        int[] assignments = hungarianAlgorithm.execute();

        int editorialCostForParentMapping = editorialCostForMapping(fixedEditorialCost, parentPartialMapping, completeSourceGraph, completeTargetGraph);
        int costMatrixSum = getCostMatrixSum(costMatrix, assignments);
        // this is the lower bound for all children of parentPartialMapping
        int lowerBoundForNextLevel = editorialCostForParentMapping + costMatrixSum;


        ReducerBasedMapping reducerBasedMapping = new ReducerBasedMapping();
        Mapping result = reducerBasedMapping.init(assignments,
                allSources,
                availableTargetVertices,
                parentLevel,
                parentPartialMapping,
                completeSourceGraph,
                completeTargetGraph,
                singleMappingToInnerEdgesInfo,
                reducedValuesByPairOfMappings,
                fixedEditorialCost);
        optimalEdit.ged = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, result, completeSourceGraph, completeTargetGraph);
        ;
        optimalEdit.mapping = result;


//        if (lowerBoundForNextLevel >= optimalEdit.ged) {
//            return;
//        }
//
//
//        Mapping newMapping = parentPartialMapping.extendMapping(v_i, availableTargetVertices.get(assignments[0]));
//        MappingEntry newMappingEntry = new MappingEntry(newMapping, level, lowerBoundForNextLevel);
//        LinkedBlockingQueue<MappingEntry> siblings = new LinkedBlockingQueue<>();
//        newMappingEntry.mappingEntriesSiblings = siblings;
//        newMappingEntry.assignments = assignments;
//        newMappingEntry.availableTargetVertices = availableTargetVertices;
//        newMappingEntry.singleMappingToInnerEdgesInfo = singleMappingToInnerEdgesInfo;
//
//        queue.add(newMappingEntry);
//
//        expandMappingAndUpdateOptimalMapping(fixedEditorialCost,
//                level,
//                optimalEdit,
//                allSources,
//                parentPartialMapping.copy(),
//                assignments,
//                availableTargetVertices,
//                lowerBoundForNextLevel,
//                singleMappingToInnerEdgesInfo,
//                reducedValuesByPairOfMappings
//        );
//
//        calculateRestOfSiblings(
//                availableTargetVertices,
//                hungarianAlgorithm,
//                costMatrix,
//                costMatrixSum,
//                editorialCostForParentMapping,
//                parentPartialMapping,
//                v_i,
//                optimalEdit,
//                level,
//                siblings,
//                singleMappingToInnerEdgesInfo,
//                reducedValuesByPairOfMappings
//        );
    }


    private void updateOptimalEdit(OptimalEdit optimalEdit, int newGed, Mapping mapping) {
        assertTrue(newGed < optimalEdit.ged);
        optimalEdit.ged = newGed;
        optimalEdit.mapping = mapping;
    }


    private void calculateRestOfSiblings(List<Vertex> availableTargetVertices,
                                         HungarianAlgorithm hungarianAlgorithm,
                                         int[][] costMatrixCopy,
                                         int costMatrixSum,
                                         int editorialCostForParentMapping,
                                         Mapping parentPartialMapping,
                                         Vertex v_i,
                                         OptimalEdit optimalEdit,
                                         int level,
                                         LinkedBlockingQueue<MappingEntry> siblings,
                                         Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo,
                                         Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings
    ) {
        // generate all siblings, which map v_i and is child of parentPartialMapping
        // starting from 1 as we already generated the first one
        for (int child = 1; child < availableTargetVertices.size(); child++) {
            int[] assignments = hungarianAlgorithm.nextChild();
            // either the original cost matrix is set to MAX_VALUE or during nextChild the modified hungarianAlgorithm.costMatrix was
            // because the target vertex was used before
            if (hungarianAlgorithm.costMatrix[0][assignments[0]] == Integer.MAX_VALUE || costMatrixCopy[0][assignments[0]] == Integer.MAX_VALUE) {
                // this means solution contains not allowed mappings, therefore we are finished
                break;
            }

            int costMatrixSumSibling = getCostMatrixSum(costMatrixCopy, assignments);
            int lowerBoundForPartialMappingSibling = editorialCostForParentMapping + costMatrixSumSibling;
            if (lowerBoundForPartialMappingSibling >= optimalEdit.ged) {
                // this means we can't find a better solution than the current one, hence we are finished
                break;
            }

            Mapping newMappingSibling = parentPartialMapping.extendMapping(v_i, availableTargetVertices.get(assignments[0]));
            // the edc of newMappingSibling is >= lowerBoundForPartialMappingSibling
            MappingEntry sibling = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMappingSibling);
            sibling.mappingEntriesSiblings = siblings;
            sibling.assignments = assignments;
            sibling.availableTargetVertices = availableTargetVertices;
            sibling.singleMappingToInnerEdgesInfo = singleMappingToInnerEdgesInfo;
            sibling.reducedValuesByPairOfMappings = reducedValuesByPairOfMappings;

            siblings.add(sibling);

            runningCheck.check();
        }
    }

    // this retrieves the next sibling  from MappingEntry.sibling and adds it to the queue if the lowerBound is less than the current upperBound
    private void addSiblingToQueue(
            int fixedEditorialCost,
            int level,
            PriorityQueue<MappingEntry> queue,
            OptimalEdit optimalEdit,
            List<Vertex> allSources,
            List<Vertex> allTargets,
            MappingEntry mappingEntry) throws InterruptedException {

        assertFalse(mappingEntry.mappingEntriesSiblings.isEmpty());
        MappingEntry sibling = mappingEntry.mappingEntriesSiblings.take();
        if (sibling.lowerBoundCost < optimalEdit.ged) {
            queue.add(sibling);

            // we need to start here from the parent mapping, this is why we remove the last element
            Mapping toExpand = sibling.partialMapping.copyMappingWithLastElementRemoved();

            expandMappingAndUpdateOptimalMapping(fixedEditorialCost,
                    level,
                    optimalEdit,
                    allSources,
                    toExpand,
                    sibling.assignments,
                    sibling.availableTargetVertices,
                    sibling.lowerBoundCost,
                    sibling.singleMappingToInnerEdgesInfo,
                    sibling.reducedValuesByPairOfMappings
            );
        }
    }

    /**
     * Extend the partial mapping to a full mapping according to the optimal
     * matching (hungarian algo result) and update the optimal edit if we
     * found a better one.
     */
    private void expandMappingAndUpdateOptimalMapping(int fixedEditorialCost,
                                                      int level,
                                                      OptimalEdit optimalEdit,
                                                      List<Vertex> allSources,
                                                      Mapping toExpand,
                                                      int[] assignments,
                                                      List<Vertex> availableTargetVertices,
                                                      int lowerBoundCost,
                                                      Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo,
                                                      Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings
    ) {
        int partialMappingEdc = editorialCostForMapping(fixedEditorialCost, toExpand, completeSourceGraph, completeTargetGraph);
        for (int i = 0; i < assignments.length; i++) {
            toExpand.add(allSources.get(level - 1 + i), availableTargetVertices.get(assignments[i]));
        }
        assertTrue(toExpand.size() == this.completeSourceGraph.size());
        int costForFullMapping = editorialCostForMapping(fixedEditorialCost, toExpand, completeSourceGraph, completeTargetGraph);
//        assertTrue(lowerBoundCost <= costForFullMapping);
        int reducedCostSum = 0;
        int diffToWorstCase = 0;
        int reducedCostCount = 0;
        for (int i = 0; i < assignments.length; i++) {
            Vertex v1 = allSources.get(level - 1 + i);
            Vertex u1 = availableTargetVertices.get(assignments[i]);
            SingleMapping sm1 = new SingleMapping(v1, u1);
            for (int j = i + 1; j < assignments.length; j++) {
                Vertex v2 = allSources.get(level - 1 + j);
                Vertex u2 = availableTargetVertices.get(assignments[j]);
                SingleMapping sm2 = new SingleMapping(v2, u2);
                Set<SingleMapping> newPair = Set.of(sm1, sm2);
                Integer reducedCost = reducedValuesByPairOfMappings.get(newPair);
                if (reducedCost != null) {
                    reducedCostSum += reducedCost;
                    reducedCostCount++;
                }
            }
            InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(new SingleMapping(v1, u1));
            if (innerEdgesInfo != null) {
                int maxCosts = innerEdgesInfo.maxCosts();
                diffToWorstCase += (maxCosts - innerEdgesInfo.minimumCosts());
            }
        }
        // lowerbound is edc of partial mapping +  anchored + min inner edge costs of unmapped
        int calculatedEdc = lowerBoundCost + diffToWorstCase - reducedCostSum;
//        System.out.println("partial mapping edc " + partialMappingEdc + " assignment sum " + (lowerBoundCost - partialMappingEdc) + " + diffToWorstCase " + diffToWorstCase + " - reducedCostSum " + reducedCostSum + " = " + calculatedEdc + " level : " + (level - startLevel) + " reduced cunter: " + reducedCostCount);
        assertTrue(calculatedEdc == costForFullMapping);

        if (costForFullMapping < optimalEdit.ged) {
//            System.out.println("UPDATE!!!!!!!");
            updateOptimalEdit(optimalEdit, costForFullMapping, toExpand);
        }
    }


    private int getCostMatrixSum(int[][] costMatrix, int[] assignments) {
        int costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i][assignments[i]];
        }
        return costMatrixSum;
    }


    /**
     * lower bound mapping cost between for v -> u in respect to a partial mapping.
     * It basically tells the minimal costs we can expect for all mappings that come from extending
     * the partial mapping with v -> u.
     * <p>
     * This is basically the formula (5) from page 6 of https://lijunchang.github.io/pdf/2022-ged-tkde.pdf.
     * <p>
     *
     * The main difference is that the formula works with undirected graphs, but we have a directed graph,
     * hence there is no 1/2 factor and for comparing the labels of anchored vertices to v/u we need to
     * take both directions into account.
     * <p>
     *
     * The other optimization is that a schema graph will have never a lot of adjacent edges compared to
     * the overall vertices count, therefore the algorithm for the anchored vertices costs iterates
     * over the adjacent edges of v/u instead of all the mapped vertices.
     * <p>
     *
     * Additionally, there is a shortcut for isolated vertices, representing deletion/insertion which is also cached.
     * <p>
     * Some naming: an anchored vertex is a vertex that is mapped via the partial mapping.
     * An inner edge is an edge between two vertices that are both not anchored (mapped).
     * The vertices v and u are by definition not mapped.
     */
    private int calcLowerBoundMappingCost(Vertex v,
                                          Vertex u,
                                          Mapping partialMapping,
                                          Map<Vertex, Integer> isolatedVerticesCache,
                                          Map<Vertex, Vertex> nonFixedParentRestrictions,
                                          Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgeInfo) {
        if (nonFixedParentRestrictions.containsKey(v) || partialMapping.hasFixedParentRestriction(v)) {
            if (!u.isIsolated()) { // Always allow mapping to isolated nodes
                Vertex uParentRestriction = nonFixedParentRestrictions.get(v);
                if (uParentRestriction == null) {
                    uParentRestriction = partialMapping.getFixedParentRestriction(v);
                }

                Collection<Edge> parentEdges = completeTargetGraph.getAdjacentEdgesInverseNonCopy(u);
                if (parentEdges.size() != 1) {
                    return Integer.MAX_VALUE;
                }

                Vertex uParent = parentEdges.iterator().next().getFrom();
                if (uParent != uParentRestriction) {
                    return Integer.MAX_VALUE;
                }
            }
        }

        if (!possibleMappings.mappingPossible(v, u)) {
            return Integer.MAX_VALUE;
        }
//        if (u.isOfType(SchemaGraph.ISOLATED)) {
//            Integer cached;
//            if ((cached = isolatedVerticesCache.get(v)) != null) {
//                return cached;
//            }
//            int result = calcLowerBoundMappingCostForIsolated(v, partialMapping, true);
//            isolatedVerticesCache.put(v, result);
//            return result;
//        }
//        if (v.isOfType(SchemaGraph.ISOLATED)) {
//            Integer cached;
//            if ((cached = isolatedVerticesCache.get(v)) != null) {
//                return cached;
//            }
//            int result = calcLowerBoundMappingCostForIsolated(u, partialMapping, false);
//            isolatedVerticesCache.put(u, result);
//            return result;
//        }

        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());

        int anchoredVerticesCost = 0;
        Multiset<String> multisetInnerEdgeLabelsV = HashMultiset.create();
        Multiset<String> multisetInnerEdgeLabelsU = HashMultiset.create();
        Multimap<String, Vertex> innerEdgeLabelToHeadV = HashMultimap.create();
        Multimap<String, Vertex> innerEdgeLabelToHeadU = HashMultimap.create();

        Collection<Edge> adjacentEdgesV = completeSourceGraph.getAdjacentEdgesNonCopy(v);
        Collection<Edge> adjacentEdgesU = completeTargetGraph.getAdjacentEdgesNonCopy(u);

        Collection<Edge> adjacentEdgesInverseV = completeSourceGraph.getAdjacentEdgesInverseNonCopy(v);
        Collection<Edge> adjacentEdgesInverseU = completeTargetGraph.getAdjacentEdgesInverseNonCopy(u);

        Set<Edge> matchedTargetEdges = new LinkedHashSet<>();
        Set<Edge> matchedTargetEdgesInverse = new LinkedHashSet<>();

        for (Edge edgeV : adjacentEdgesV) {

            Vertex targetTo = partialMapping.getTarget(edgeV.getTo());
            if (targetTo == null) {
                // meaning it is an inner edge(not part of the subgraph induced by the partial mapping)
                multisetInnerEdgeLabelsV.add(edgeV.getLabel());
                innerEdgeLabelToHeadV.put(edgeV.getLabel(), edgeV.getTo());
                continue;
            }
            /* question is if the edge from v is mapped onto an edge from u
              (also edge are not mapped directly, but the vertices are)
              and if the adjacent edge is mapped onto an adjacent edge,
              we need to check the labels of the edges
             */
            Edge matchedTargetEdge = completeTargetGraph.getEdge(u, targetTo);
            if (matchedTargetEdge != null) {
                matchedTargetEdges.add(matchedTargetEdge);
                if (!Objects.equals(edgeV.getLabel(), matchedTargetEdge.getLabel())) {
                    anchoredVerticesCost++;
                }
            } else {
//            // no matching adjacent edge from u found means there is no
//            // edge from edgeV.getTo() to mapped(edgeV.getTo())
//            // and we need to increase the costs
                anchoredVerticesCost++;
            }

        }

        for (Edge edgeV : adjacentEdgesInverseV) {

            Vertex targetFrom = partialMapping.getTarget(edgeV.getFrom());
            // we are only interested in edges from anchored vertices
            if (targetFrom == null) {
                continue;
            }
            Edge matachedTargetEdge = completeTargetGraph.getEdge(targetFrom, u);
            if (matachedTargetEdge != null) {
                matchedTargetEdgesInverse.add(matachedTargetEdge);
                if (!Objects.equals(edgeV.getLabel(), matachedTargetEdge.getLabel())) {
                    anchoredVerticesCost++;
                }
            } else {
                anchoredVerticesCost++;
            }
        }

        for (Edge edgeU : adjacentEdgesU) {
            // test if this is an inner edge (meaning it not part of the subgraph induced by the partial mapping)
            // we know that u is not part of the mapped vertices, therefore we only need to test the "to" vertex
            if (!partialMapping.containsTarget(edgeU.getTo())) {
                multisetInnerEdgeLabelsU.add(edgeU.getLabel());
                innerEdgeLabelToHeadU.put(edgeU.getLabel(), edgeU.getTo());
                continue;
            }
            if (matchedTargetEdges.contains(edgeU)) {
                continue;
            }
            anchoredVerticesCost++;

        }
        for (Edge edgeU : adjacentEdgesInverseU) {
            // we are only interested in edges from anchored vertices
            if (!partialMapping.containsTarget(edgeU.getFrom()) || matchedTargetEdgesInverse.contains(edgeU)) {
                continue;
            }
            anchoredVerticesCost++;
        }

        if (innerEdgeLabelToHeadV.size() > 0 || innerEdgeLabelToHeadU.size() > 0) {
            singleMappingToInnerEdgeInfo.put(new SingleMapping(v, u), new InnerEdgesInfo(innerEdgeLabelToHeadV, innerEdgeLabelToHeadU));
        }

        Multiset<String> intersection = Multisets.intersection(multisetInnerEdgeLabelsV, multisetInnerEdgeLabelsU);
        int multiSetEditDistance = Math.max(multisetInnerEdgeLabelsV.size(), multisetInnerEdgeLabelsU.size()) - intersection.size();

        int result = (equalNodes ? 0 : 1) + multiSetEditDistance + anchoredVerticesCost;
        return result;
    }

    /**
     * Simplified lower bound calc if the source/target vertex is isolated
     */
    private int calcLowerBoundMappingCostForIsolated(Vertex vertex,
                                                     Mapping partialMapping,
                                                     boolean sourceOrTarget
    ) {
        SchemaGraph schemaGraph = sourceOrTarget ? completeSourceGraph : completeTargetGraph;

        // every adjacent edge is inserted/deleted for an isolated vertex
        Collection<Edge> adjacentEdges = schemaGraph.getAdjacentEdgesNonCopy(vertex);

        // for the inverse adjacent edges we only count the anchored ones
        int anchoredInverseEdges = 0;
        Collection<Edge> adjacentEdgesInverse = schemaGraph.getAdjacentEdgesInverseNonCopy(vertex);
        for (Edge edge : adjacentEdgesInverse) {
            if (partialMapping.contains(edge.getFrom(), sourceOrTarget)) {
                anchoredInverseEdges++;
            }
        }
        return 1 + adjacentEdges.size() + anchoredInverseEdges;
    }


    private static Integer conditionDerivation(Mapping fullMapping, InnerEdgesInfo.SetsMapping setsMapping) {
        int counter = 0;
        for (Vertex v : setsMapping.from) {
            Vertex u = fullMapping.getTarget(v);
            if (setsMapping.to.contains(u)) {
                counter++;
            }
        }
        if (setsMapping.sameLabels) {
            return (Math.min(setsMapping.from.size(), setsMapping.to.size()) - counter) * 2;
        } else {
            return (Math.min(setsMapping.from.size(), setsMapping.to.size()) - counter);
        }
    }


}
