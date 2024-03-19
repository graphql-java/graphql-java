package graphql.schema.diffing;

import com.google.common.collect.HashMultiset;
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

    private static class MappingEntry {
        public LinkedBlockingQueue<MappingEntry> mappingEntriesSiblings = new LinkedBlockingQueue<>();
        public int[] assignments;

        /**
         * These are the available vertices, relative to the parent mapping.
         * Meaning the last mapped element is NOT contained in it.
         */
        public List<Vertex> availableTargetVertices;

        Mapping partialMapping;
        int level; // = partialMapping.size
        double lowerBoundCost;


        public MappingEntry(Mapping partialMapping, int level, double lowerBoundCost) {
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

            if (mappingEntry.lowerBoundCost >= optimalEdit.ged) {
                // once the lowest lowerBoundCost is not lower than the optimal edit, we are done
                break;
            }

            if (mappingEntry.level > 0 && !mappingEntry.mappingEntriesSiblings.isEmpty()) {
                addSiblingToQueue(
                        fixedEditorialCost,
                        mappingEntry.level,
                        queue,
                        optimalEdit,
                        allSources,
                        allTargets,
                        mappingEntry);
            }
            if (mappingEntry.level < graphSize) {
                addChildToQueue(
                        fixedEditorialCost,
                        mappingEntry,
                        queue,
                        optimalEdit,
                        allSources,
                        allTargets
                );
            }

            runningCheck.check();
        }

        return optimalEdit;
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
        double[][] costMatrixForHungarianAlgo = new double[costMatrixSize][costMatrixSize];
        double[][] costMatrix = new double[costMatrixSize][costMatrixSize];

        Map<Vertex, Double> isolatedVerticesCache = new LinkedHashMap<>();
        Map<Vertex, Vertex> nonFixedParentRestrictions = possibleMappingsCalculator.getNonFixedParentRestrictions(completeSourceGraph, completeTargetGraph, parentPartialMapping);

        for (int i = parentLevel; i < allSources.size(); i++) {
            Vertex v = allSources.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                double cost = calcLowerBoundMappingCost(v, u, parentPartialMapping, isolatedVerticesCache, nonFixedParentRestrictions);
                costMatrixForHungarianAlgo[i - parentLevel][j] = cost;
                costMatrix[i - parentLevel][j] = cost;
                j++;
            }
            runningCheck.check();
        }

        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrixForHungarianAlgo);
        int[] assignments = hungarianAlgorithm.execute();
        int editorialCostForMapping = editorialCostForMapping(fixedEditorialCost, parentPartialMapping, completeSourceGraph, completeTargetGraph);
        double costMatrixSum = getCostMatrixSum(costMatrix, assignments);
        double lowerBoundForPartialMapping = editorialCostForMapping + costMatrixSum;

        Mapping newMapping = parentPartialMapping.extendMapping(v_i, availableTargetVertices.get(assignments[0]));

        if (lowerBoundForPartialMapping >= optimalEdit.ged) {
            return;
        }
        MappingEntry newMappingEntry = new MappingEntry(newMapping, level, lowerBoundForPartialMapping);
        LinkedBlockingQueue<MappingEntry> siblings = new LinkedBlockingQueue<>();
        newMappingEntry.mappingEntriesSiblings = siblings;
        newMappingEntry.assignments = assignments;
        newMappingEntry.availableTargetVertices = availableTargetVertices;

        queue.add(newMappingEntry);

        expandMappingAndUpdateOptimalMapping(fixedEditorialCost,
                level,
                optimalEdit,
                allSources,
                parentPartialMapping.copy(),
                assignments,
                availableTargetVertices,
                lowerBoundForPartialMapping);

        calculateRestOfChildren(
                availableTargetVertices,
                hungarianAlgorithm,
                costMatrix,
                editorialCostForMapping,
                parentPartialMapping,
                v_i,
                optimalEdit.ged,
                level,
                siblings
        );
    }

    private void updateOptimalEdit(OptimalEdit optimalEdit, int newGed, Mapping mapping) {
        assertTrue(newGed < optimalEdit.ged);
        optimalEdit.ged = newGed;
        optimalEdit.mapping = mapping;
    }

    // generate all children mappings and save in MappingEntry.sibling
    private void calculateRestOfChildren(List<Vertex> availableTargetVertices,
                                         HungarianAlgorithm hungarianAlgorithm,
                                         double[][] costMatrixCopy,
                                         double editorialCostForMapping,
                                         Mapping partialMapping,
                                         Vertex v_i,
                                         int upperBound,
                                         int level,
                                         LinkedBlockingQueue<MappingEntry> siblings
    ) {
        // starting from 1 as we already generated the first one
        for (int child = 1; child < availableTargetVertices.size(); child++) {
            int[] assignments = hungarianAlgorithm.nextChild();
            if (hungarianAlgorithm.costMatrix[0][assignments[0]] == Integer.MAX_VALUE) {
                break;
            }

            double costMatrixSumSibling = getCostMatrixSum(costMatrixCopy, assignments);
            double lowerBoundForPartialMappingSibling = editorialCostForMapping + costMatrixSumSibling;
            Mapping newMappingSibling = partialMapping.extendMapping(v_i, availableTargetVertices.get(assignments[0]));


            if (lowerBoundForPartialMappingSibling >= upperBound) {
                break;
            }
            MappingEntry sibling = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMappingSibling);
            sibling.mappingEntriesSiblings = siblings;
            sibling.assignments = assignments;
            sibling.availableTargetVertices = availableTargetVertices;

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
                    sibling.lowerBoundCost);
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
                                                      double lowerBoundCost) {
        for (int i = 0; i < assignments.length; i++) {
            toExpand.add(allSources.get(level - 1 + i), availableTargetVertices.get(assignments[i]));
        }
        assertTrue(toExpand.size() == this.completeSourceGraph.size());
        int costForFullMapping = editorialCostForMapping(fixedEditorialCost, toExpand, completeSourceGraph, completeTargetGraph);
        assertTrue(lowerBoundCost <= costForFullMapping);
        if (costForFullMapping < optimalEdit.ged) {
            updateOptimalEdit(optimalEdit, costForFullMapping, toExpand);
        }
    }


    private double getCostMatrixSum(double[][] costMatrix, int[] assignments) {
        double costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i][assignments[i]];
        }
        return costMatrixSum;
    }

    /**
     * a partial mapping introduces a sub graph. The editorial cost is only calculated with respect to this sub graph.
     */


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
    private double calcLowerBoundMappingCost(Vertex v,
                                             Vertex u,
                                             Mapping partialMapping,
                                             Map<Vertex, Double> isolatedVerticesCache,
                                             Map<Vertex, Vertex> nonFixedParentRestrictions) {
        if (nonFixedParentRestrictions.containsKey(v) || partialMapping.hasParentRestriction(v)) {
            if (!u.isIsolated()) { // Always allow mapping to isolated nodes
                Vertex uParentRestriction = nonFixedParentRestrictions.get(v);
                if (uParentRestriction == null) {
                    uParentRestriction = partialMapping.getParentRestriction(v);
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
        if (u.isOfType(SchemaGraph.ISOLATED)) {
            if (isolatedVerticesCache.containsKey(v)) {
                return isolatedVerticesCache.get(v);
            }
            double result = calcLowerBoundMappingCostForIsolated(v, partialMapping, true);
            isolatedVerticesCache.put(v, result);
            return result;
        }
        if (v.isOfType(SchemaGraph.ISOLATED)) {
            if (isolatedVerticesCache.containsKey(u)) {
                return isolatedVerticesCache.get(u);
            }
            double result = calcLowerBoundMappingCostForIsolated(u, partialMapping, false);
            isolatedVerticesCache.put(u, result);
            return result;
        }

        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());

        Collection<Edge> adjacentEdgesV = completeSourceGraph.getAdjacentEdgesNonCopy(v);
        Multiset<String> multisetLabelsV = HashMultiset.create();

        for (Edge edge : adjacentEdgesV) {
            // test if this is an inner edge (meaning it not part of the subgraph induced by the partial mapping)
            // we know that v is not part of the mapped vertices, therefore we only need to test the "to" vertex
            if (!partialMapping.containsSource(edge.getTo())) {
                multisetLabelsV.add(edge.getLabel());
            }
        }

        Collection<Edge> adjacentEdgesU = completeTargetGraph.getAdjacentEdgesNonCopy(u);
        Multiset<String> multisetLabelsU = HashMultiset.create();
        for (Edge edge : adjacentEdgesU) {
            // test if this is an inner edge (meaning it not part of the subgraph induced by the partial mapping)
            // we know that u is not part of the mapped vertices, therefore we only need to test the "to" vertex
            if (!partialMapping.containsTarget(edge.getTo())) {
                multisetLabelsU.add(edge.getLabel());
            }
        }

        int anchoredVerticesCost = calcAnchoredVerticesCost(v, u, partialMapping);

        Multiset<String> intersection = Multisets.intersection(multisetLabelsV, multisetLabelsU);
        int multiSetEditDistance = Math.max(multisetLabelsV.size(), multisetLabelsU.size()) - intersection.size();

        double result = (equalNodes ? 0 : 1) + multiSetEditDistance + anchoredVerticesCost;
        return result;
    }


    private int calcAnchoredVerticesCost(Vertex v,
                                         Vertex u,
                                         Mapping partialMapping) {
        int anchoredVerticesCost = 0;

        Collection<Edge> adjacentEdgesV = completeSourceGraph.getAdjacentEdgesNonCopy(v);
        Collection<Edge> adjacentEdgesU = completeTargetGraph.getAdjacentEdgesNonCopy(u);

        Collection<Edge> adjacentEdgesInverseV = completeSourceGraph.getAdjacentEdgesInverseNonCopy(v);
        Collection<Edge> adjacentEdgesInverseU = completeTargetGraph.getAdjacentEdgesInverseNonCopy(u);

        Set<Edge> matchedTargetEdges = new LinkedHashSet<>();
        Set<Edge> matchedTargetEdgesInverse = new LinkedHashSet<>();

        outer:
        for (Edge edgeV : adjacentEdgesV) {
            // we are only interested in edges from anchored vertices
            if (!partialMapping.containsSource(edgeV.getTo())) {
                continue;
            }
            for (Edge edgeU : adjacentEdgesU) {
                // looking for an adjacent edge from u matching it
                if (partialMapping.getTarget(edgeV.getTo()) == edgeU.getTo()) {
                    matchedTargetEdges.add(edgeU);
                    // found two adjacent edges, comparing the labels
                    if (!Objects.equals(edgeV.getLabel(), edgeU.getLabel())) {
                        anchoredVerticesCost++;
                    }
                    continue outer;
                }
            }
            // no matching adjacent edge from u found means there is no
            // edge from edgeV.getTo() to mapped(edgeV.getTo())
            // and we need to increase the costs
            anchoredVerticesCost++;

        }

        outer:
        for (Edge edgeV : adjacentEdgesInverseV) {
            // we are only interested in edges from anchored vertices
            if (!partialMapping.containsSource(edgeV.getFrom())) {
                continue;
            }
            for (Edge edgeU : adjacentEdgesInverseU) {
                if (partialMapping.getTarget(edgeV.getFrom()) == edgeU.getFrom()) {
                    matchedTargetEdgesInverse.add(edgeU);
                    if (!Objects.equals(edgeV.getLabel(), edgeU.getLabel())) {
                        anchoredVerticesCost++;
                    }
                    continue outer;
                }
            }
            anchoredVerticesCost++;

        }

        /**
         * what is missing now is all edges from u (and inverse), which have not been matched.
         */
        for (Edge edgeU : adjacentEdgesU) {
            // we are only interested in edges from anchored vertices
            if (!partialMapping.containsTarget(edgeU.getTo()) || matchedTargetEdges.contains(edgeU)) {
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

        return anchoredVerticesCost;
    }


    /**
     * Simplified lower bound calc if the source/target vertex is isolated
     */
    private double calcLowerBoundMappingCostForIsolated(Vertex vertex,
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


}
