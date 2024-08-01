package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import graphql.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    //    List<SingleMapping> mappingsToIntroduce = new ArrayList<>();
//    List<Integer> edcLists = new ArrayList<>();
    int wholeMappingEdc;
    //    EdcCalculation wholeMappingEdcCalculation;
    private PossibleMappingsCalculator.PossibleMappings possibleMappings;
    private Map<Vertex, Vertex> nonFixedParentRestrictions;

    public Mapping init(int[] assignments,
                        List<Vertex> allSources,
                        List<Vertex> availableTargetVertices,
                        int parentLevel,
                        Mapping parentPartialMapping,
                        SchemaGraph completeSourceGraph,
                        SchemaGraph completeTargetGraph,
                        Map<SingleMapping, InnerEdgesInfo> singleMappingToInnerEdgesInfo,
                        Map<Set<SingleMapping>, Integer> reducedValuesByPairOfMappings,
                        int fixedEditorialCost,
                        PossibleMappingsCalculator.PossibleMappings possibleMappings,
                        Map<Vertex, Vertex> nonFixedParentRestrictions,
                        HungarianAlgorithm hungarianAlgorithm,
                        int[][] costMatrix) {
        this.nonFixedParentRestrictions = nonFixedParentRestrictions;
        this.possibleMappings = possibleMappings;
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

        int originalSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            originalSum += costMatrix[i][assignments[i]];
        }
        System.out.println("original sum: " + originalSum);

//        Map<Vertex, List<Set<SingleMapping>>> pairsWithSourceVertex = new LinkedHashMap<>();
//        Map<Vertex, Set<Set<SingleMapping>>> pairsWithoutVertex = new LinkedHashMap<>();
//        Map<Vertex, List<Set<SingleMapping>>> pairsByTargetVertex = new LinkedHashMap<>();
//        for (Set<SingleMapping> pair : reducedValuesByPairOfMappings.keySet()) {
//            SingleMapping first = getFirst(pair);
//            SingleMapping second = getSecond(pair);
//            pairsWithSourceVertex.computeIfAbsent(first.getFrom(), k -> new ArrayList<>()).add(pair);
//            pairsWithSourceVertex.computeIfAbsent(second.getFrom(), k -> new ArrayList<>()).add(pair);
//            pairsByTargetVertex.computeIfAbsent(first.getTo(), k -> new ArrayList<>()).add(pair);
//            pairsByTargetVertex.computeIfAbsent(second.getTo(), k -> new ArrayList<>()).add(pair);
//
//            for (Set<SingleMapping> pair2 : reducedValuesByPairOfMappings.keySet()) {
//                SingleMapping first2 = getFirst(pair2);
//                SingleMapping second2 = getSecond(pair2);
//                // checking all 4 vertices
//                if (!first.getFrom().equals(first2.getFrom()) && !first.getFrom().equals(second2.getFrom())) {
//                    pairsWithoutVertex.computeIfAbsent(first.getFrom(), k -> new LinkedHashSet<>()).add(pair2);
//                }
//                if (!first.getTo().equals(first2.getTo()) && !first.getTo().equals(second2.getTo())) {
//                    pairsWithoutVertex.computeIfAbsent(first.getTo(), k -> new LinkedHashSet<>()).add(pair2);
//                }
//                if (!second.getFrom().equals(first2.getFrom()) && !second.getFrom().equals(second2.getFrom())) {
//                    pairsWithoutVertex.computeIfAbsent((second.getFrom()), k -> new LinkedHashSet<>()).add(pair2);
//                }
//                if (!second.getTo().equals(first2.getTo()) && !second.getTo().equals(second2.getTo())) {
//                    pairsWithoutVertex.computeIfAbsent((second.getTo()), k -> new LinkedHashSet<>()).add(pair2);
//                }
//            }
//        }
//        Set<Set<SingleMapping>> pairs = new LinkedHashSet<>();
//        for (Vertex vertex : pairsWithSourceVertex.keySet()) {
//            if (pairs.size() == 0) {
//                pairs.addAll(pairsWithoutVertex.get(vertex));
//            } else {
//                pairs.retainAll(pairsWithoutVertex.get(vertex));
//            }
//            System.out.println("pairs size: " + pairs.size());
////            System.out.println("number of pairs without it: " + pairsWithoutVertex.get(vertex).size());
//        }
//        System.out.println("different source vertices: " + pairsWithSourceVertex.size());
//        System.out.println("different target vertices: " + pairsWithSourceVertex.size());

        AtomicInteger result = new AtomicInteger();
        List<Set<SingleMapping>> keys = new ArrayList<>(reducedValuesByPairOfMappings.keySet());
        LinkedHashMap<Set<SingleMapping>, Integer> setIntegerLinkedHashMap = new LinkedHashMap<>();
        for (Set<SingleMapping> pair : reducedValuesByPairOfMappings.keySet()) {
            if (reducedValuesByPairOfMappings.get(pair) > 1) {
                setIntegerLinkedHashMap.put(pair, reducedValuesByPairOfMappings.get(pair));
            }
        }
//        calcPossibleReducers(keys, setIntegerLinkedHashMap, HashBiMap.create(), 0, 0, result);
        calcPossibleReducers2(reducedValuesByPairOfMappings, HashBiMap.create(), 0, 0, result);
        System.out.println("result size: " + result);


        int counter = 0;
        while (true) {
            if (counter > 0) {
                assignments = hungarianAlgorithm.nextBestSolution();
            }

            wholeMapping = parentPartialMapping.copy();
            int sum = 0;
            int rowsAvailableForReduction = 0;
            for (int i = 0; i < assignments.length; i++) {
                sum += costMatrix[i][assignments[i]];
                SingleMapping sm = new SingleMapping(allSources.get(parentLevel + i), availableTargetVertices.get(assignments[i]));
                InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(sm);
                if (innerEdgesInfo != null && innerEdgesInfo.minimumCosts() < innerEdgesInfo.maxCosts()) {
                    rowsAvailableForReduction++;
                }
                wholeMapping.add(allSources.get(parentLevel + i), availableTargetVertices.get(assignments[i]));
            }
            System.out.println(counter + " sum: " + sum);
            Mapping originalStartMapping = wholeMapping.copy();

            wholeMappingEdc = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, wholeMapping, completeSourceGraph, completeTargetGraph);
//            wholeMappingEdcCalculation = reducerBasedEdc(wholeMapping);
            System.out.println("start wholeMapping edc: " + wholeMappingEdc + " with rows available for reduction: " + rowsAvailableForReduction);

            Set<Map.Entry<Set<SingleMapping>, Integer>> entries = new LinkedHashSet<>(reducedValuesByPairOfMappings.entrySet());
//            boolean changed = improveWholeMapping(entries);
            improveWholeMappingImpl(entries, wholeMapping, wholeMappingEdc);
//            if (!changed) {
//                break;
//            }
            System.out.println("end with: " + wholeMappingEdc);
            if (counter == 0) {
                return null;
            }
            counter++;
        }

//        return wholeMapping;
    }


    private boolean improveWholeMapping(Set<Map.Entry<Set<SingleMapping>, Integer>> reducedValuesByPairOfMappings) {
        boolean improved = false;
        while (true) {
            EdcAndMapping bestEdcAndMappingForThisRound = null;
            for (Map.Entry<Set<SingleMapping>, Integer> entry : reducedValuesByPairOfMappings) {
                SingleMapping one = getFirst(entry.getKey());
                SingleMapping two = getSecond(entry.getKey());
                EdcAndMapping edcAndMapping;
                if (wholeMapping.getTarget(one.getFrom()) != one.getTo() && wholeMapping.getTarget(two.getFrom()) != two.getTo()) {
//                    edcAndMapping = tryChanging(one);
//                    edcAndMapping = tryChanging(two);
//                    System.out.println("try changing both with edc: " + edcAndMapping.edc);
                    continue;
//                    if (bestEdcAndMappingForThisRound != null && edcAndMapping.edc < bestEdcAndMappingForThisRound.edc) {
////                        System.out.println("success in both changing with edc: " + edcAndMapping.edc);
//                    }
                } else if (wholeMapping.getTarget(one.getFrom()) != one.getTo()) {
                    edcAndMapping = tryChanging(one, wholeMapping);
                } else if (wholeMapping.getTarget(two.getFrom()) != two.getTo()) {
                    edcAndMapping = tryChanging(two, wholeMapping);
                } else {
                    continue;
                }
//                System.out.println("try changing one with edc: " + edcAndMapping.edc);
                if (bestEdcAndMappingForThisRound == null || edcAndMapping.edc < bestEdcAndMappingForThisRound.edc) {
                    bestEdcAndMappingForThisRound = edcAndMapping;
                    EdcCalculation edcCalculation = reducerBasedEdc(bestEdcAndMappingForThisRound.mapping);
                    Assert.assertTrue(edcCalculation.edc == bestEdcAndMappingForThisRound.edc);
                }
            }
            if (bestEdcAndMappingForThisRound == null) {
                break;
            }
            if (bestEdcAndMappingForThisRound.edc >= wholeMappingEdc) {
                break;
            }
            EdcCalculation edcCalculation = reducerBasedEdc(bestEdcAndMappingForThisRound.mapping);
//            System.out.println("new best mapping with edc: " + edcCalculation);
            wholeMapping = bestEdcAndMappingForThisRound.mapping;
//            mappingsToIntroduce.add(bestEdcAndMappingForThisRound.mappingToAdd);
            wholeMappingEdc = bestEdcAndMappingForThisRound.edc;
//            edcLists.add(wholeMappingEdc);
            improved = true;
        }
        return improved;
    }

    static class Improved {
        Map.Entry<Set<SingleMapping>, Integer> entry;
        Mapping mapping;
        int edc;
        private final MappingChange mappingChange;
        private final SingleMapping mappingToAdd;

        public Improved(Map.Entry<Set<SingleMapping>, Integer> entry,
                        Mapping mapping,
                        int edc,
                        MappingChange mappingChange,
                        SingleMapping mappingToAdd) {
            this.entry = entry;
            this.mapping = mapping;
            this.edc = edc;
            this.mappingChange = mappingChange;

            this.mappingToAdd = mappingToAdd;
        }
    }

    private void calcPossibleReducers2(Map<Set<SingleMapping>, Integer> availableReducer, BiMap<Vertex, Vertex> partialMapping, int sum, int level, AtomicInteger result) {
        Map<SingleMapping, Set<Set<SingleMapping>>> availableReducerBySingleMapping = new LinkedHashMap<>();
        for (Set<SingleMapping> pair : availableReducer.keySet()) {
            SingleMapping first = getFirst(pair);
            SingleMapping second = getSecond(pair);
            availableReducerBySingleMapping.computeIfAbsent(first, k -> new LinkedHashSet<>()).add(pair);
            availableReducerBySingleMapping.computeIfAbsent(second, k -> new LinkedHashSet<>()).add(pair);
        }
        ArrayList<SingleMapping> keys = new ArrayList<>(availableReducerBySingleMapping.keySet());
        keys.sort((o1, o2) -> {
            return Integer.compare(availableReducerBySingleMapping.get(o1).size(), availableReducerBySingleMapping.get(o2).size());
        });
        for (SingleMapping key : keys) {
            System.out.println("key: " + " with size: " + availableReducerBySingleMapping.get(key).size());
        }

        LinkedHashMap<Set<SingleMapping>, Integer> setIntegerLinkedHashMap = new LinkedHashMap<>();
        for (SingleMapping key : keys) {
            for (Set<SingleMapping> pair : availableReducerBySingleMapping.get(key)) {
                setIntegerLinkedHashMap.put(pair, availableReducer.get(pair));
            }
        }
        calcPossibleReducers(availableReducer, HashBiMap.create(), 0, 0, new AtomicInteger());
    }

    private void calcPossibleReducers(Map<Set<SingleMapping>, Integer> availableReducer, BiMap<Vertex, Vertex> partialMapping, int sum, int level, AtomicInteger result) {

//        if (availableReducer.size() == 0) {
//            System.out.println("finished mapping partialMapping size: " + partialMapping.size() + " with sum: " + sum);
//            return;
//        }
//        System.out.println("mapping partialMapping size: " + partialMapping.size() + " with sum: " + sum + " at level " + level);
        int count = 0;
        boolean foundAnyReducer = false;
        if (sum >= result.get()) {
            System.out.println("found result at " + level + " with sum: " + sum);
            result.set(sum);
        }
//        if(level == 17) {
//            System.out.println("level: " + level + " with sum: " + sum + " with partialMapping size: " + partialMapping.size() + " with availableReducer size: " + availableReducer.size());
//        }
        for (Set<SingleMapping> newPairToAdd : availableReducer.keySet()) {
            SingleMapping sm1 = getFirst(newPairToAdd);
            SingleMapping sm2 = getSecond(newPairToAdd);

            boolean containsSM1From = partialMapping.containsKey(sm1.getFrom());
            boolean containsSM1To = partialMapping.containsValue(sm1.getTo());
            boolean containsSM2From = partialMapping.containsKey(sm2.getFrom());
            boolean containsSM2To = partialMapping.containsValue(sm2.getTo());
            HashBiMap<Vertex, Vertex> newPartialMapping = null;
            boolean foundReducer = false;
            if (!containsSM1From && !containsSM1To && !containsSM2From && !containsSM2To) {
                foundReducer = true;
                newPartialMapping = HashBiMap.create(partialMapping);
                newPartialMapping.put(sm1.getFrom(), sm1.getTo());
                newPartialMapping.put(sm2.getFrom(), sm2.getTo());
            } else if (!containsSM1From && !containsSM1To && partialMapping.get(sm2.getFrom()) == sm2.getTo()) {
                foundReducer = true;
                newPartialMapping = HashBiMap.create(partialMapping);
                newPartialMapping.put(sm1.getFrom(), sm1.getTo());
            } else if (partialMapping.get(sm1.getFrom()) == sm1.getTo() && !containsSM2From && !containsSM2To) {
                foundReducer = true;
                newPartialMapping = HashBiMap.create(partialMapping);
                newPartialMapping.put(sm2.getFrom(), sm2.getTo());
            }
            if (foundReducer) {
                foundAnyReducer = true;
                LinkedHashMap<Set<SingleMapping>, Integer> setIntegerLinkedHashMap = new LinkedHashMap<>();
                for (Set<SingleMapping> toCheck : availableReducer.keySet()) {
                    SingleMapping toCheckSM1 = getFirst(toCheck);
                    SingleMapping toCheckSM2 = getSecond(toCheck);
                    boolean containsToCheckSM1From = newPartialMapping.containsKey(toCheckSM1.getFrom());
                    boolean containsToCheckSM1To = newPartialMapping.containsValue(toCheckSM1.getTo());
                    boolean containsToCheckSM2From = newPartialMapping.containsKey(toCheckSM2.getFrom());
                    boolean containsToCheckSM2To = newPartialMapping.containsValue(toCheckSM2.getTo());
                    if (!containsToCheckSM1From && !containsToCheckSM1To && !containsToCheckSM2From && !containsToCheckSM2To) {
                        setIntegerLinkedHashMap.put(toCheck, availableReducer.get(toCheck));
                    } else if (!containsToCheckSM1From && !containsToCheckSM1To && newPartialMapping.get(toCheckSM2.getFrom()) == toCheckSM2.getTo()) {
                        setIntegerLinkedHashMap.put(toCheck, availableReducer.get(toCheck));
                    } else if (newPartialMapping.get(toCheckSM1.getFrom()) == toCheckSM1.getTo() && !containsToCheckSM2From && !containsToCheckSM2To) {
                        setIntegerLinkedHashMap.put(toCheck, availableReducer.get(toCheck));
                    }
                }
                calcPossibleReducers(setIntegerLinkedHashMap, newPartialMapping, sum + availableReducer.get(newPairToAdd), level + 1, result);
            }
            count++;
        }
//        if (!foundAnyReducer) {

//        }
    }


    private boolean improveWholeMappingImpl(Set<Map.Entry<Set<SingleMapping>, Integer>> reducedValuesByPairOfMappings,
                                            Mapping currentMapping,
                                            int currentBestEdc
    ) {
        if (reducedValuesByPairOfMappings.size() == 0) {
//            System.out.println("best edc: " + currentBestEdc);
            return false;
        }
        boolean foundBetter = false;
        int foundBetterCount = 0;
//        System.out.println("start with current best edc: " + currentBestEdc);
        Multimap<Integer, Improved> improvedByEdc = LinkedHashMultimap.create();
        int smallestOfAllEdc = currentBestEdc;
        Mapping smallestMapping = null;
        Set<SingleMapping> processed = new LinkedHashSet<>();
        for (Map.Entry<Set<SingleMapping>, Integer> entry : reducedValuesByPairOfMappings) {
            SingleMapping one = getFirst(entry.getKey());
            SingleMapping two = getSecond(entry.getKey());
            EdcAndMapping edcAndMapping;
            SingleMapping mappingToAdd = null;
            if (currentMapping.getTarget(one.getFrom()) != one.getTo() && currentMapping.getTarget(two.getFrom()) != two.getTo()) {
//                    edcAndMapping = tryChanging(one);
//                    edcAndMapping = tryChanging(two);
//                    System.out.println("try changing both with edc: " + edcAndMapping.edc);
                continue;
            } else if (currentMapping.getTarget(one.getFrom()) == one.getTo() && currentMapping.getTarget(two.getFrom()) != two.getTo()) {
                mappingToAdd = two;
            } else if (currentMapping.getTarget(one.getFrom()) != one.getTo() && currentMapping.getTarget(two.getFrom()) == two.getTo()) {
                mappingToAdd = one;
            } else {
                continue;
            }
            if (processed.contains(mappingToAdd)) {
                continue;
            }
            processed.add(mappingToAdd);
            edcAndMapping = tryChanging(mappingToAdd, currentMapping);
            if (edcAndMapping.edc < currentBestEdc) {
                smallestOfAllEdc = Math.min(smallestOfAllEdc, edcAndMapping.edc);
                smallestMapping = edcAndMapping.mapping;
                improvedByEdc.put(edcAndMapping.edc, new Improved(entry, edcAndMapping.mapping, edcAndMapping.edc, edcAndMapping.mappingChange, edcAndMapping.mappingToAdd));
            }
        }
        if (smallestMapping != null) {
            System.out.println("smallest edc value " + smallestOfAllEdc + " " + reducerBasedEdc(smallestMapping));
        }
        for (Improved improved : improvedByEdc.get(smallestOfAllEdc)) {
            LinkedHashSet<Map.Entry<Set<SingleMapping>, Integer>> entries = new LinkedHashSet<>(reducedValuesByPairOfMappings);
            entries.remove(improved.entry);
            improveWholeMappingImpl(entries, improved.mapping, improved.edc);
        }

//        if(!foundBetter) {
//            System.out.println("best edc: " + currentBestEdc);
//        }
        return foundBetter;
    }


    private Multimap<SingleMapping, Map<SingleMapping, Integer>> calcCrossingsRight(Mapping mapping) {

        Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsToRight = LinkedHashMultimap.create();
        List<Vertex> sources = new ArrayList<>(mapping.getMap().keySet());
        for (int i = 0; i < sources.size(); i++) {
            Vertex v1 = sources.get(i);
            Vertex u1 = mapping.getTarget(v1);
            SingleMapping sm1 = new SingleMapping(v1, u1);
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

//    static class SingleMappingAndReducedCost {
//        final SingleMapping singleMapping;
//        final Integer reducedCost;
//
//        public SingleMappingAndReducedCost(SingleMapping singleMapping, Integer reducedCost) {
//            this.singleMapping = singleMapping;
//            this.reducedCost = reducedCost;
//        }
//    }

    class EdcAndMapping {
        Mapping mapping;
        int edc;
        SingleMapping mappingToAdd;
        MappingChange mappingChange;

        public EdcAndMapping(Mapping mapping, int edc, SingleMapping mappingToAdd, MappingChange mappingChange) {
            this.mapping = mapping;
            this.edc = edc;
            this.mappingToAdd = mappingToAdd;
            this.mappingChange = mappingChange;
        }
    }

    private EdcAndMapping tryChanging(SingleMapping newMappingToIntroduce, Mapping mappingToChange) {

        MappingChange mappingChange = calcChangeToIntroduceNewSingleMapping(mappingToChange, newMappingToIntroduce, false);
//
        Mapping newMapping = mappingToChange.copy();
        newMapping.remove(mappingChange.mappingToRemove1.getFrom());
        newMapping.remove(mappingChange.mappingToRemove2.getFrom());
        newMapping.add(mappingChange.mappingToAdd1.getFrom(), mappingChange.mappingToAdd1.getTo());
        newMapping.add(mappingChange.mappingToAdd2.getFrom(), mappingChange.mappingToAdd2.getTo());

        int newEdc = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, newMapping, completeSourceGraph, completeTargetGraph);
        return new EdcAndMapping(newMapping,
                newEdc,
                newMappingToIntroduce,
                mappingChange);
    }

    private int diagonalCost(SingleMapping singleMapping) {
        int cost = EditorialCostForMapping.anchoredCost(singleMapping.getFrom(), singleMapping.getTo(), parentPartialMapping, completeSourceGraph, completeTargetGraph);
        InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(singleMapping);
        Assert.assertTrue(possibleMappings.mappingPossible(singleMapping.getFrom(), singleMapping.getTo()));
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
        final int addDiagonalCost1;
        final int addDiagonalCost2;
        final List<Integer> addedReducer1;
        final List<Integer> addedReducer2;
        final int removedDiagonalCost1;
        final int removedDiagonalCost2;
        final List<Integer> removedReducer1;
        final List<Integer> removedReducer2;

        public MappingChange(SingleMapping mappingToAdd1,
                             SingleMapping mappingToAdd2,
                             SingleMapping mappingToRemove1,
                             SingleMapping mappingToRemove2,
                             int addDiagonalCost1,
                             int addDiagonalCost2,
                             List<Integer> addedReducer1,
                             List<Integer> addedReducer2,
                             int removedDiagonalCost1,
                             int removedDiagonalCost2,
                             List<Integer> removedReducer1,
                             List<Integer> removedReducer2) {
            this.mappingToAdd1 = mappingToAdd1;
            this.mappingToAdd2 = mappingToAdd2;
            this.mappingToRemove1 = mappingToRemove1;
            this.mappingToRemove2 = mappingToRemove2;
            this.addDiagonalCost1 = addDiagonalCost1;
            this.addDiagonalCost2 = addDiagonalCost2;
            this.addedReducer1 = addedReducer1;
            this.addedReducer2 = addedReducer2;
            this.removedDiagonalCost1 = removedDiagonalCost1;
            this.removedDiagonalCost2 = removedDiagonalCost2;
            this.removedReducer1 = removedReducer1;
            this.removedReducer2 = removedReducer2;
        }
    }


    MappingChange calcChangeToIntroduceNewSingleMapping(Mapping mappingToChange, SingleMapping newMappingToAdd, boolean calcEdcDiff) {
        Vertex source1 = mappingToChange.getSource(newMappingToAdd.getTo());
        Vertex target1 = mappingToChange.getTarget(newMappingToAdd.getFrom());


        SingleMapping mappingToAdd1 = newMappingToAdd;
        SingleMapping mappingToAdd2 = new SingleMapping(source1, target1);
        SingleMapping mappingToRemove1 = new SingleMapping(newMappingToAdd.getFrom(), mappingToChange.getTarget(newMappingToAdd.getFrom()));
        SingleMapping mappingToRemove2 = new SingleMapping(mappingToChange.getSource(newMappingToAdd.getTo()), newMappingToAdd.getTo());

        int removedDiagonalCost1 = diagonalCost(mappingToRemove1);
        int removedDiagonalCost2 = diagonalCost(mappingToRemove2);
        int addDiagonalCost1 = diagonalCost(mappingToAdd1);
        int addDiagonalCost2 = diagonalCost(mappingToAdd2);
        List<Integer> removedReducer1 = new ArrayList<>();
        List<Integer> removedReducer2 = new ArrayList<>();
        List<Integer> addedReducer1 = new ArrayList<>();
        List<Integer> addedReducer2 = new ArrayList<>();

        if (calcEdcDiff) {
            List<Vertex> sources = new ArrayList<>(mappingToChange.getMap().keySet());
            for (int i = 0; i < sources.size(); i++) {
                Vertex v1 = sources.get(i);
                Vertex u1 = mappingToChange.getTarget(v1);
                SingleMapping sm1 = new SingleMapping(v1, u1);

                // all pairs right of the sm1
                for (int j = i + 1; j < sources.size(); j++) {
                    Vertex v2 = sources.get(j);
                    Vertex u2 = mappingToChange.getTarget(v2);
                    SingleMapping sm2 = new SingleMapping(v2, u2);
                    Set<SingleMapping> newPair = Set.of(sm1, sm2);
                    Integer reducedCost = reducedValuesByPairOfMappings.get(newPair);
                    if (reducedCost != null) {
                        if (sm1.equals(mappingToRemove1) || sm2.equals(mappingToRemove1)) {
                            removedReducer1.add(reducedCost);
                        }
                        if (sm1.equals(mappingToRemove2) || sm2.equals(mappingToRemove2)) {
                            removedReducer2.add(reducedCost);
                        }
                        if ((sm1.equals(mappingToRemove1) || sm2.equals(mappingToRemove1)) &&
                                (sm1.equals(mappingToRemove2) || sm2.equals(mappingToRemove2))) {
                            throw new RuntimeException("yohoh");
                        }
                    }
                }

                for (Set<SingleMapping> pair : reducedValuesByPairOfMappings.keySet()) {
                    SingleMapping first = getFirst(pair);
                    SingleMapping second = getSecond(pair);
                    Integer value = reducedValuesByPairOfMappings.get(pair);
                    if (sm1.equals(first) && second.equals(mappingToAdd1)) {
                        addedReducer1.add(value);
                    } else if (sm1.equals(second) && first.equals(mappingToAdd1)) {
                        addedReducer1.add(value);
                    }
                    if (sm1.equals(first) && second.equals(mappingToAdd2)) {
                        addedReducer2.add(value);
                    } else if (sm1.equals(second) && first.equals(mappingToAdd2)) {
                        addedReducer2.add(value);
                    }
                }
            }
        }

        return new MappingChange(newMappingToAdd,
                mappingToAdd2,
                mappingToRemove1,
                mappingToRemove2,
                addDiagonalCost1,
                addDiagonalCost2,
                addedReducer1,
                addedReducer2,
                removedDiagonalCost1,
                removedDiagonalCost2,
                removedReducer1,
                removedReducer2);
    }

    static class EdcCalculation {

        private final int reducedCount;

        public EdcCalculation(int edc,
                              int diagonalSum,
                              int anchoredCostSum,
                              int innerEdgesMaxSum,
                              int reducedSum,
                              int reducedCount,
                              Map<Integer, Map<Integer, Integer>> reducerPerRow,
                              int[] diagonalValues) {
            this.edc = edc;
            this.diagonalSum = diagonalSum;
            this.anchoredCostSum = anchoredCostSum;
            this.innerEdgesMaxSum = innerEdgesMaxSum;
            this.reducedSum = reducedSum;
            this.reducedCount = reducedCount;
            this.reducerPerRow = reducerPerRow;
            this.diagonalValues = diagonalValues;
        }

        int edc;
        int diagonalSum;
        int anchoredCostSum;
        int innerEdgesMaxSum;

        int reducedSum;


        Map<Integer, Map<Integer, Integer>> reducerPerRow;
        int[] diagonalValues;

        @Override
        public String toString() {
            return "EdcCalculation{" +
                    "reducedCount=" + reducedCount +
                    ", edc=" + edc +
                    ", diagonalSum=" + diagonalSum +
                    ", anchoredCostSum=" + anchoredCostSum +
                    ", innerEdgesMaxSum=" + innerEdgesMaxSum +
                    ", reducedSum=" + reducedSum +
                    ", reducerPerRow=" + reducerPerRow +
                    ", diagonalValues=" + Arrays.toString(diagonalValues) +
                    '}';
        }
    }

    EdcCalculation reducerBasedEdc(Mapping wholeMapping) {
        int[] diagonalValues = new int[wholeMapping.nonFixedSize()];
        int diagonalSum = 0;
        int innerEdgesMaxSum = 0;
        int anchoredCostSum = 0;
        Multimap<SingleMapping, Map<SingleMapping, Integer>> crossingsRight = calcCrossingsRight(wholeMapping);
        List<SingleMapping> singleMappings = new ArrayList<>();
        for (int row = 0; row < wholeMapping.nonFixedSize(); row++) {
            Vertex v = new ArrayList<>(wholeMapping.getMap().keySet()).get(row);
            Vertex u = wholeMapping.getTarget(v);
            SingleMapping singleMappingForCurrentRow = new SingleMapping(v, u);
            singleMappings.add(singleMappingForCurrentRow);

            int diagonalValue = diagonalCost(singleMappingForCurrentRow);
            anchoredCostSum += EditorialCostForMapping.anchoredCost(v, u, parentPartialMapping, completeSourceGraph, completeTargetGraph);
            InnerEdgesInfo innerEdgesInfo = singleMappingToInnerEdgesInfo.get(singleMappingForCurrentRow);
            if (innerEdgesInfo != null) {
                innerEdgesMaxSum += innerEdgesInfo.maxCosts();
            }

            diagonalValues[row] = diagonalValue;
            diagonalSum += diagonalValue;

        }
        Map<Integer, Map<Integer, Integer>> reducerPerRow = new LinkedHashMap<>();
        int reducedSum = 0;
        int reducedCount = 0;
        for (int i = 0; i < singleMappings.size(); i++) {
            SingleMapping singleMapping = singleMappings.get(i);
            Collection<Map<SingleMapping, Integer>> crossings = crossingsRight.get(singleMapping);
            for (Map<SingleMapping, Integer> otherSingleMappingAndReducedValue : crossings) {
                SingleMapping otherSingleMapping = getFirst(otherSingleMappingAndReducedValue.keySet());
                int reducedValue = otherSingleMappingAndReducedValue.values().iterator().next();
                int row = singleMappings.indexOf(otherSingleMapping);
                reducerPerRow.computeIfAbsent(i, k -> new LinkedHashMap<>()).put(row, reducedValue);
                reducedSum += reducedValue;
                reducedCount++;
            }
        }

        int edc = fixedEditorialCost + (diagonalSum - reducedSum);
        int edcOldSchool = EditorialCostForMapping.editorialCostForMapping(fixedEditorialCost, wholeMapping, completeSourceGraph, completeTargetGraph);
        Assert.assertTrue(edc == edcOldSchool);
        return new EdcCalculation(edc, diagonalSum, anchoredCostSum, innerEdgesMaxSum, reducedSum, reducedCount, reducerPerRow, diagonalValues);
    }

    void diffEdcCalculation(EdcCalculation edcCalculation1, EdcCalculation edcCalculation2) {
        if (edcCalculation1.diagonalSum != edcCalculation2.diagonalSum) {
            System.out.println("diagonal sum changed from " + edcCalculation1.diagonalSum + " to " + edcCalculation2.diagonalSum);
        }
        if (edcCalculation1.reducedCount != edcCalculation2.reducedCount) {
            System.out.println("reduced count changed from " + edcCalculation1.reducedCount + " to " + edcCalculation2.reducedCount);
        }
        if (edcCalculation1.reducedSum != edcCalculation2.reducedSum) {
            System.out.println("reduced sum changed from " + edcCalculation1.reducedSum + " to " + edcCalculation2.reducedSum);
        }
//        for(int i = 0; i < edcCalculation1.diagonalValues.length; i++) {
//            if(edcCalculation1.diagonalValues[i] != edcCalculation2.diagonalValues[i]) {
//                System.out.println("diagonal value at " + i + " changed from " + edcCalculation1.diagonalValues[i] + " to " + edcCalculation2.diagonalValues[i]);
//            }
//        }

    }

}
