package graphql.schema.diffing;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.AtomicDoubleArray;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.EditorialCostForMapping.editorialCostForMapping;

@Internal
public class DiffImpl {

    private static final MappingEntry LAST_ELEMENT = new MappingEntry();
    private final SchemaGraph completeSourceGraph;
    private final SchemaGraph completeTargetGraph;
    private final FillupIsolatedVertices.IsolatedVertices isolatedVertices;
    private final SchemaDiffingRunningCheck runningCheck;

    private static class MappingEntry {
        public boolean siblingsFinished;
        public LinkedBlockingQueue<MappingEntry> mappingEntriesSiblings;
        public int[] assignments;
        public List<Vertex> availableTargetVertices;

        Mapping partialMapping = new Mapping();
        int level; // = partialMapping.size
        double lowerBoundCost;

        public MappingEntry(Mapping partialMapping, int level, double lowerBoundCost) {
            this.partialMapping = partialMapping;
            this.level = level;
            this.lowerBoundCost = lowerBoundCost;
        }

        public MappingEntry() {

        }
    }

    public static class OptimalEdit {
        public List<Mapping> mappings = new ArrayList<>();
        public List<List<EditOperation>> listOfEditOperations = new ArrayList<>();

        public List<Set<EditOperation>> listOfSets = new ArrayList<>();

        public int ged = Integer.MAX_VALUE;

        public OptimalEdit() {

        }

        public OptimalEdit(List<Mapping> mappings, List<List<EditOperation>> listOfEditOperations, int ged) {
            this.mappings = mappings;
            this.listOfEditOperations = listOfEditOperations;
            this.ged = ged;
        }
    }

    public DiffImpl(SchemaGraph completeSourceGraph, SchemaGraph completeTargetGraph, FillupIsolatedVertices.IsolatedVertices isolatedVertices, SchemaDiffingRunningCheck runningCheck) {
        this.completeSourceGraph = completeSourceGraph;
        this.completeTargetGraph = completeTargetGraph;
        this.isolatedVertices = isolatedVertices;
        this.runningCheck = runningCheck;
    }

    OptimalEdit diffImpl(Mapping startMapping, List<Vertex> relevantSourceList, List<Vertex> relevantTargetList) throws Exception {
        int graphSize = relevantSourceList.size();

        ArrayList<EditOperation> initialEditOperations = new ArrayList<>();
        int mappingCost = editorialCostForMapping(startMapping, completeSourceGraph, completeTargetGraph, initialEditOperations);
        int level = startMapping.size();
        MappingEntry firstMappingEntry = new MappingEntry(startMapping, level, mappingCost);
        System.out.println("first entry: lower bound: " + mappingCost + " at level " + level);

        OptimalEdit optimalEdit = new OptimalEdit();
        PriorityQueue<MappingEntry> queue = new PriorityQueue<>((mappingEntry1, mappingEntry2) -> {
            int compareResult = Double.compare(mappingEntry1.lowerBoundCost, mappingEntry2.lowerBoundCost);
            if (compareResult == 0) {
                return Integer.compare(mappingEntry2.level, mappingEntry1.level);
            } else {
                return compareResult;
            }
        });
        queue.add(firstMappingEntry);
        firstMappingEntry.siblingsFinished = true;
//        queue.add(new MappingEntry());
//        int counter = 0;
        while (!queue.isEmpty()) {
            MappingEntry mappingEntry = queue.poll();
//            System.out.println((++counter) + " check entry at level " + mappingEntry.level + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost + " map " + getDebugMap(mappingEntry.partialMapping));
//            if ((++counter) % 100 == 0) {
//                System.out.println((counter) + " entry at level");
//            }
            if (mappingEntry.lowerBoundCost >= optimalEdit.ged) {
                continue;
            }
            if (mappingEntry.level > 0 && !mappingEntry.siblingsFinished) {
                addSiblingToQueue(
                        mappingEntry.level,
                        queue,
                        optimalEdit,
                        relevantSourceList,
                        relevantTargetList,
                        mappingEntry);
            }
            if (mappingEntry.level < graphSize) {
                addChildToQueue(mappingEntry,
                        queue,
                        optimalEdit,
                        relevantSourceList,
                        relevantTargetList
                );
            }

            runningCheck.check();
        }

        return optimalEdit;
    }


    // this calculates all children for the provided parentEntry, but only the first is directly added to the queue
    private void addChildToQueue(MappingEntry parentEntry,
                                 PriorityQueue<MappingEntry> queue,
                                 OptimalEdit optimalEdit,
                                 List<Vertex> sourceList,
                                 List<Vertex> targetList

    ) {
        Mapping partialMapping = parentEntry.partialMapping;
        int level = parentEntry.level;

        assertTrue(level == partialMapping.size());

        ArrayList<Vertex> availableTargetVertices = new ArrayList<>(targetList);
        availableTargetVertices.removeAll(partialMapping.getTargets());
        assertTrue(availableTargetVertices.size() + partialMapping.size() == targetList.size());
        Vertex v_i = sourceList.get(level);

        // the cost matrix is for the non mapped vertices
        int costMatrixSize = sourceList.size() - level;

        // costMatrix gets modified by the hungarian algorithm ... therefore we create two of them
        AtomicDoubleArray[] costMatrixForHungarianAlgo = new AtomicDoubleArray[costMatrixSize];
        Arrays.setAll(costMatrixForHungarianAlgo, (index) -> new AtomicDoubleArray(costMatrixSize));
        AtomicDoubleArray[] costMatrix = new AtomicDoubleArray[costMatrixSize];
        Arrays.setAll(costMatrix, (index) -> new AtomicDoubleArray(costMatrixSize));

        // we are skipping the first level -i indices
        Set<Vertex> partialMappingSourceSet = new LinkedHashSet<>(partialMapping.getSources());
        Set<Vertex> partialMappingTargetSet = new LinkedHashSet<>(partialMapping.getTargets());


        for (int i = level; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int j = 0;
            for (Vertex u : availableTargetVertices) {
                double cost = calcLowerBoundMappingCost(v, u, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet);
                costMatrixForHungarianAlgo[i - level].set(j, cost);
                costMatrix[i - level].set(j, cost);
                j++;
            }

            runningCheck.check();
        }
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrixForHungarianAlgo);

        int[] assignments = hungarianAlgorithm.execute();
        int editorialCostForMapping = editorialCostForMapping(partialMapping, completeSourceGraph, completeTargetGraph, new ArrayList<>());
        double costMatrixSum = getCostMatrixSum(costMatrix, assignments);


        double lowerBoundForPartialMapping = editorialCostForMapping + costMatrixSum;
        int v_i_target_IndexSibling = assignments[0];
        Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
        Mapping newMappingSibling = partialMapping.extendMapping(v_i, bestExtensionTargetVertexSibling);


        if (lowerBoundForPartialMapping >= optimalEdit.ged) {
            return;
        }
        MappingEntry newMappingEntry = new MappingEntry(newMappingSibling, level + 1, lowerBoundForPartialMapping);
        LinkedBlockingQueue<MappingEntry> siblings = new LinkedBlockingQueue<>();
        newMappingEntry.mappingEntriesSiblings = siblings;
        newMappingEntry.assignments = assignments;
        newMappingEntry.availableTargetVertices = availableTargetVertices;

        queue.add(newMappingEntry);
        Mapping fullMapping = partialMapping.copy();
        for (int i = 0; i < assignments.length; i++) {
            fullMapping.add(sourceList.get(level + i), availableTargetVertices.get(assignments[i]));
        }

        List<EditOperation> editOperations = new ArrayList<>();
        int costForFullMapping = editorialCostForMapping(fullMapping, completeSourceGraph, completeTargetGraph, editOperations);
        updateOptimalEdit(optimalEdit, costForFullMapping, fullMapping, editOperations);

        calculateRestOfChildren(
                availableTargetVertices,
                hungarianAlgorithm,
                costMatrix,
                editorialCostForMapping,
                partialMapping,
                v_i,
                optimalEdit.ged,
                level + 1,
                siblings
        );
    }

    private void updateOptimalEdit(OptimalEdit optimalEdit, int newGed, Mapping mapping, List<EditOperation> editOperations) {
        if (newGed < optimalEdit.ged) {
            optimalEdit.ged = newGed;

            optimalEdit.listOfEditOperations.clear();
            optimalEdit.listOfEditOperations.add(editOperations);

            optimalEdit.listOfSets.clear();
            optimalEdit.listOfSets.add(new LinkedHashSet<>(editOperations));

            optimalEdit.mappings.clear();
            optimalEdit.mappings.add(mapping);
            System.out.println("setting new best edit at level " + (mapping.size()) + " with size " + editOperations.size());
        } else if (newGed == optimalEdit.ged) {
            Set<EditOperation> newSet = new LinkedHashSet<>(editOperations);
            for (Set<EditOperation> set : optimalEdit.listOfSets) {
                if (set.equals(newSet)) {
                    return;
                }
            }
            optimalEdit.listOfSets.add(newSet);
            optimalEdit.listOfEditOperations.add(editOperations);
            optimalEdit.mappings.add(mapping);
        }
    }

    // generate all children mappings and save in MappingEntry.sibling
    private void calculateRestOfChildren(List<Vertex> availableTargetVertices,
                                         HungarianAlgorithm hungarianAlgorithm,
                                         AtomicDoubleArray[] costMatrixCopy,
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

            siblings.add(sibling);

            runningCheck.check();
        }
        siblings.add(LAST_ELEMENT);

    }

    // this retrieves the next sibling  from MappingEntry.sibling and adds it to the queue if the lowerBound is less than the current upperBound
    private void addSiblingToQueue(
            int level,
            PriorityQueue<MappingEntry> queue,
            OptimalEdit optimalEdit,
            List<Vertex> sourceList,
            List<Vertex> targetGraph,
            MappingEntry mappingEntry) throws InterruptedException {

        MappingEntry sibling = mappingEntry.mappingEntriesSiblings.take();
        if (sibling == LAST_ELEMENT) {
            mappingEntry.siblingsFinished = true;
            return;
        }
        if (sibling.lowerBoundCost < optimalEdit.ged) {
//            System.out.println("adding new sibling entry " + getDebugMap(sibling.partialMapping) + "  at level " + level + " with candidates left: " + sibling.availableTargetVertices.size() + " at lower bound: " + sibling.lowerBoundCost);

            queue.add(sibling);

            // we need to start here from the parent mapping, this is why we remove the last element
            Mapping fullMapping = sibling.partialMapping.removeLastElement();
            for (int i = 0; i < sibling.assignments.length; i++) {
                fullMapping.add(sourceList.get(level - 1 + i), sibling.availableTargetVertices.get(sibling.assignments[i]));
            }
//            assertTrue(fullMapping.size() == this.sourceGraph.size());
            List<EditOperation> editOperations = new ArrayList<>();
            int costForFullMapping = editorialCostForMapping(fullMapping, completeSourceGraph, completeTargetGraph, editOperations);
            updateOptimalEdit(optimalEdit, costForFullMapping, fullMapping, editOperations);
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

    /**
     * a partial mapping introduces a sub graph. The editorial cost is only calculated with respect to this sub graph.
     */

    // lower bound mapping cost between for v -> u in respect to a partial mapping
    // this is BMa
    private double calcLowerBoundMappingCost(Vertex v,
                                             Vertex u,
                                             List<Vertex> partialMappingSourceList,
                                             Set<Vertex> partialMappingSourceSet,
                                             List<Vertex> partialMappingTargetList,
                                             Set<Vertex> partialMappingTargetSet

    ) {
        if (!isolatedVertices.mappingPossible(v, u)) {
            return Integer.MAX_VALUE;
        }
        boolean equalNodes = v.getType().equals(u.getType()) && v.getProperties().equals(u.getProperties());

        // inner edge labels of u (resp. v) in regards to the partial mapping: all labels of edges
        // which are adjacent of u (resp. v) which are inner edges
        List<Edge> adjacentEdgesV = completeSourceGraph.getAdjacentEdges(v);
        Multiset<String> multisetLabelsV = HashMultiset.create();

        for (Edge edge : adjacentEdgesV) {
            // test if this an inner edge: meaning both edges vertices are part of the non mapped vertices
            // or: at least one edge is part of the partial mapping
            if (!partialMappingSourceSet.contains(edge.getFrom()) && !partialMappingSourceSet.contains(edge.getTo())) {
                multisetLabelsV.add(edge.getLabel());
            }
        }

        List<Edge> adjacentEdgesU = completeTargetGraph.getAdjacentEdges(u);
        Multiset<String> multisetLabelsU = HashMultiset.create();
        for (Edge edge : adjacentEdgesU) {
            // test if this is an inner edge (meaning it not part of the subgraph induced by the partial mapping)
            if (!partialMappingTargetSet.contains(edge.getFrom()) && !partialMappingTargetSet.contains(edge.getTo())) {
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

            Edge sourceEdge = completeSourceGraph.getEdge(v, vPrime);
            String labelSourceEdge = sourceEdge != null ? sourceEdge.getLabel() : null;
            Edge targetEdge = completeTargetGraph.getEdge(u, mappedVPrime);
            String labelTargetEdge = targetEdge != null ? targetEdge.getLabel() : null;
            if (!Objects.equals(labelSourceEdge, labelTargetEdge)) {
                anchoredVerticesCost++;
            }

            Edge sourceEdgeInverse = completeSourceGraph.getEdge(vPrime, v);
            String labelSourceEdgeInverse = sourceEdgeInverse != null ? sourceEdgeInverse.getLabel() : null;
            Edge targetEdgeInverse = completeTargetGraph.getEdge(mappedVPrime, u);
            String labelTargetEdgeInverse = targetEdgeInverse != null ? targetEdgeInverse.getLabel() : null;
            if (!Objects.equals(labelSourceEdgeInverse, labelTargetEdgeInverse)) {
                anchoredVerticesCost++;
            }

            runningCheck.check();
        }

        Multiset<String> intersection = Multisets.intersection(multisetLabelsV, multisetLabelsU);
        int multiSetEditDistance = Math.max(multisetLabelsV.size(), multisetLabelsU.size()) - intersection.size();

        double result = (equalNodes ? 0 : 1) + multiSetEditDistance + anchoredVerticesCost;
        return result;
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


}
