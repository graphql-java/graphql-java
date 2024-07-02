package graphql.schema.diffing;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ReducerBasedMapping {


    private int[] assignments;
    private List<Vertex> allSources;
    private List<Vertex> availableTargetVertices;
    private int parentLevel;
    private Mapping parentPartialMapping;
    private SchemaGraph completeSourceGraph;
    private SchemaGraph completeTargetGraph;
    private Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo;
    private Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings;
    private int fixedEditorialCost;
    Mapping wholeMapping;
    int wholeMappingEdc;

    public Mapping init(int[] assignments,
                        List<Vertex> allSources,
                        List<Vertex> availableTargetVertices,
                        int parentLevel,
                        Mapping parentPartialMapping,
                        SchemaGraph completeSourceGraph,
                        SchemaGraph completeTargetGraph,
                        Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo,
                        Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings,
                        int fixedEditorialCost
    ) {
        this.assignments = assignments;
        this.allSources = allSources;
        this.availableTargetVertices = availableTargetVertices;
        this.parentLevel = parentLevel;
        this.parentPartialMapping = parentPartialMapping;
        this.completeSourceGraph = completeSourceGraph;
        this.completeTargetGraph = completeTargetGraph;
        this.singleMappingToInnerEdgesInfo = singleMappingToInnerEdgesInfo;
        this.reducedValuesByPairOfMappings = reducedValuesByPairOfMappings;
        this.fixedEditorialCost = fixedEditorialCost;
        Map<SingleMapping, Integer> diagonalCosts = new LinkedHashMap<>();
        List<SingleMapping> diagonal = new ArrayList<>();
        Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsOnTop = LinkedHashMultimap.create();

        wholeMapping = parentPartialMapping.copy();

        for (int i = 0; i < assignments.length; i++) {
            wholeMapping.add(allSources.get(parentLevel + i), availableTargetVertices.get(assignments[i]));
        }
        wholeMappingEdc = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, wholeMapping, completeSourceGraph, completeTargetGraph);
        System.out.println("wholeMapping edc: " + wholeMappingEdc);

//        int diagonalSum = diagonalCosts.values().stream().mapToInt(value -> value).sum();
//        System.out.println("total diagonal: " + diagonalCosts.size() + " with sum: " + diagonalSum);
//        System.out.println("total crossingsToRight: " + crossingsToRight.size());
//
//        int reducedFromRight = 0;
//        for (Map<SingleMapping, Integer> singleMappingAndReducedCost : crossingsToRight.values()) {
//            reducedFromRight += singleMappingAndReducedCost.values().iterator().next();
//        }
//        System.out.println("reducedFromRight from right: " + reducedFromRight);
//        int reducedOnTop = 0;
//        for (Map<SingleMapping, Integer> singleMappingAndReducedCost : crossingsOnTop.values()) {
//            reducedOnTop += singleMappingAndReducedCost.values().iterator().next();
//        }
//        System.out.println("reducedOnTop from top: " + reducedOnTop);
//
//        System.out.println("edc: " + (fixedEditorialCost + (diagonalSum - reducedFromRight)));

        boolean oneChanged = true;
        while (oneChanged) {
            oneChanged = false;
            for (int row = 0; row < wholeMapping.nonFixedSize(); row++) {
                Vertex v = new ArrayList<>(wholeMapping.getMap().keySet()).get(row);
                Vertex u = wholeMapping.getTarget(v);
                SingleMapping firstSingleMapping = new SingleMapping(v, u);
                Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsToRight = calcCrossingsRight(wholeMapping);

                Set<SingleMapping> rowElements = new LinkedHashSet<>(crossingsToRight.get(firstSingleMapping).stream().map(map -> getFirst(map.keySet())).collect(Collectors.toSet()));
////            System.out.println("row: " + row + "===");
//            System.out.println("actual crossingsToRight for row: " + rowElements.size());

                for (Set<SingleMapping> pair : reducedValuesByPairOfMappings.keySet()) {
                    if (pair.contains(firstSingleMapping)) {
                        SingleMapping one = getFirst(pair);
                        SingleMapping two = getSecond(pair);
                        SingleMapping otherSingleMapping = one.equals(firstSingleMapping) ? two : one;
                        if (!rowElements.contains(otherSingleMapping)) {
                            AtomicReference<MappingChange> mappingChangeAtomicReference = new AtomicReference<>();
                            boolean hasChanged = tryChanging(otherSingleMapping, crossingsToRight, crossingsOnTop, mappingChangeAtomicReference);
                            if (hasChanged) {
                                oneChanged = true;
                            }
                        }
                    }
                }
            }
        }

        System.out.println("end");
        return wholeMapping;


    }

    private Multimap<SingleMapping, Map<SingleMapping, Integer>> calcCrossingsRight(Mapping mapping) {

        Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsToRight = LinkedHashMultimap.create();
        List<Vertex> sources = new ArrayList<>(mapping.getMap().keySet());
        for (int i = 0; i < sources.size(); i++) {
            Vertex v1 = sources.get(i);
            Vertex u1 = mapping.getTarget(v1);
            SingleMapping sm1 = new SingleMapping(v1, u1);
//            int anchoredCosts = EditorialCostForMapping.anchoredCost(v1, u1, parentPartialMapping, completeSourceGraph, completeTargetGraph);
//            InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(sm1);
//            if (innerEdgesInfo != null) {
//                int maxCosts = innerEdgesInfo.maxCosts();
//                anchoredCosts += (maxCosts);
//            }
//            diagonal.add(sm1);
//            diagonalCosts.put(sm1, anchoredCosts);

            // all pairs right of the sm1
            for (int j = i + 1; j < sources.size(); j++) {
                Vertex v2 = sources.get(j);
                Vertex u2 = mapping.getTarget(v2);
                SingleMapping sm2 = new SingleMapping(v2, u2);
                Set<SingleMapping> newPair = Set.of(sm1, sm2);
                Integer reducedCost = reducedValuesByPairOfMappings.get(newPair);
                if (reducedCost != null) {
                    crossingsToRight.put(sm1, Map.of(sm2, reducedCost));
                }
            }

//            // all pairs left (or on top of sm1)
//            for (int j = i - 1; j >= 0; j--) {
//                Vertex v2 = allSources.get(parentLevel + j);
//                Vertex u2 = availableTargetVertices.get(assignments[j]);
//                SingleMapping sm2 = new SingleMapping(v2, u2);
//                Set<SingleMapping> newPair = Set.of(sm1, sm2);
//                Integer reducedCost = reducedValuesByPairOfMappings.get(newPair);
//                if (reducedCost != null) {
//                    crossingsOnTop.put(sm1, Map.of(sm2, reducedCost));
//                }
//            }

        }
        return crossingsToRight;
    }

    static <T> T getFirst(Collection<? extends T> collection) {
        return collection.iterator().next();
    }

    static <T> T getSecond(Collection<? extends T> collection) {
        Iterator<? extends T> iterator = collection.iterator();
        iterator.next();
        return iterator.next();
    }

    static class SingleMappingAndReducedCost {
        final SingleMapping singleMapping;
        final Integer reducedCost;

        public SingleMappingAndReducedCost(SingleMapping singleMapping, Integer reducedCost) {
            this.singleMapping = singleMapping;
            this.reducedCost = reducedCost;
        }
    }


    private boolean tryChanging(SingleMapping newMappingToIntroduce,
                                Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsToRight,
                                Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsOnTop,
                                AtomicReference<MappingChange> mappingChangeRef
    ) {

        MappingChange mappingChange = calcChangeToIntroduceNewSingleMapping(wholeMapping, newMappingToIntroduce);
//        System.out.println("adding 1 " + mappingChange.mappingToAdd1);
//        System.out.println("adding 2 " + mappingChange.mappingToAdd2);
//        System.out.println("remove 1 " + mappingChange.mappingToRemove1);
//        System.out.println("remove 2 " + mappingChange.mappingToRemove2);
//
        Mapping newMapping = wholeMapping.copy();
        newMapping.remove(mappingChange.mappingToRemove1.getFrom());
        newMapping.remove(mappingChange.mappingToRemove2.getFrom());
        newMapping.add(mappingChange.mappingToAdd1.getFrom(), mappingChange.mappingToAdd1.getTo());
        newMapping.add(mappingChange.mappingToAdd2.getFrom(), mappingChange.mappingToAdd2.getTo());

        int newEdc = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, newMapping, completeSourceGraph, completeTargetGraph);
//        System.out.println("new edc: " + newEdc);

        if (newEdc < wholeMappingEdc) {
            wholeMapping = newMapping;
            wholeMappingEdc = newEdc;
            System.out.println("better edc: " + newEdc);
            mappingChangeRef.set(mappingChange);
            return true;

//            Multimap<SingleMapping, Map<SingleMapping, Integer>> newCrossingsRight = LinkedHashMultimap.create(crossingsToRight);
//            retur
        }
        return false;


//        int addingCost1 = diagonalCost(mappingChange.mappingToAdd1);
//        int addingCost2 = diagonalCost(mappingChange.mappingToAdd2);
//        int removeCost1 = diagonalCost(mappingChange.mappingToRemove1);
//        int removeCost2 = diagonalCost(mappingChange.mappingToRemove2);
//
//        System.out.println("adding diag 1 cost: " + addingCost1);
//        System.out.println("adding diag 2 cost: " + addingCost2);
//        System.out.println("removing diag 1 cost: " + removeCost1);
//        System.out.println("removing diag 2 cost: " + removeCost2);
//
//        Multimap<SingleMapping, Map<SingleMapping, Integer>> newCrossingsRight = LinkedHashMultimap.create(crossingsToRight);
//        Multimap<SingleMapping, Map<SingleMapping, Integer>> newCrossingsOnTop = LinkedHashMultimap.create(crossingsOnTop);
//
//
//        for (Set<SingleMapping> pair : reducedValuesByPairOfMappings.keySet()) {
//            if (pair.contains(mappingChange.mappingToAdd1) || pair.contains(mappingChange.mappingToRemove2)) {
//                System.out.println("removing pair: " + pair);
//                reducedValuesByPairOfMappings.remove(pair);
//            }
//        }


    }

    private int diagonalCost(SingleMapping singleMapping) {
        int cost = EditorialCostForMapping.anchoredCost(singleMapping.getFrom(), singleMapping.getTo(), parentPartialMapping, completeSourceGraph, completeTargetGraph);
        InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(singleMapping);
        if (innerEdgesInfo != null) {
            int maxCosts = innerEdgesInfo.maxCosts();
            cost += (maxCosts);
        }
        return cost;
    }

    static class MappingChange {
        SingleMapping mappingToAdd1;
        SingleMapping mappingToAdd2;

        SingleMapping mappingToRemove1;
        SingleMapping mappingToRemove2;

        public MappingChange(SingleMapping mappingToAdd1, SingleMapping mappingToAdd2, SingleMapping mappingToRemove1, SingleMapping mappingToRemove2) {
            this.mappingToAdd1 = mappingToAdd1;
            this.mappingToAdd2 = mappingToAdd2;
            this.mappingToRemove1 = mappingToRemove1;
            this.mappingToRemove2 = mappingToRemove2;
        }
    }


    MappingChange calcChangeToIntroduceNewSingleMapping(Mapping mappingToChange, SingleMapping newMappingToAdd) {
        Vertex source1 = mappingToChange.getSource(newMappingToAdd.getTo());
        Vertex target1 = mappingToChange.getTarget(newMappingToAdd.getFrom());

        return new MappingChange(newMappingToAdd,
                new SingleMapping(source1, target1),
                new SingleMapping(newMappingToAdd.getFrom(), mappingToChange.getTarget(newMappingToAdd.getFrom())),
                new SingleMapping(mappingToChange.getSource(newMappingToAdd.getTo()), newMappingToAdd.getTo()));
    }


}
