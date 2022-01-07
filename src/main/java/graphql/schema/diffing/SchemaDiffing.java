package graphql.schema.diffing;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.AtomicDoubleArray;
import graphql.Assert;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import static java.util.Collections.emptySet;
import static java.util.Collections.synchronizedMap;

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

    private void diffNamedList(Collection<Vertex> sourceVertices,
                               Collection<Vertex> targetVertices,
                               List<Vertex> deleted, // sourceVertices
                               List<Vertex> inserted, // targetVertices
                               BiMap<Vertex, Vertex> same) {
        Map<String, Vertex> sourceByName = FpKit.groupingByUniqueKey(sourceVertices, vertex -> vertex.get("name"));
        Map<String, Vertex> targetByName = FpKit.groupingByUniqueKey(targetVertices, vertex -> vertex.get("name"));
        for (Vertex sourceVertex : sourceVertices) {
            Vertex targetVertex = targetByName.get((String) sourceVertex.get("name"));
            if (targetVertex == null) {
                deleted.add(sourceVertex);
            } else {
                same.put(sourceVertex, targetVertex);
            }
        }

        for (Vertex targetVertex : targetVertices) {
            if (sourceByName.get((String) targetVertex.get("name")) == null) {
                inserted.add(targetVertex);
            }
        }
    }

    private void diffNamedList(Set<String> sourceNames,
                               Set<String> targetNames,
                               List<String> deleted,
                               List<String> inserted,
                               List<String> same) {
        for (String sourceName : sourceNames) {
            if (targetNames.contains(sourceName)) {
                same.add(sourceName);
            } else {
                deleted.add(sourceName);
            }
        }

        for (String targetName : targetNames) {
            if (!sourceNames.contains(targetName)) {
                inserted.add(targetName);
            }
        }
    }

    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        int sizeDiff = targetGraph.size() - sourceGraph.size();
        System.out.println("graph diff: " + sizeDiff);
        Map<String, Set<Vertex>> isolatedSourceVertices = new LinkedHashMap<>();
        Map<String, Set<Vertex>> isolatedTargetVertices = new LinkedHashMap<>();
        Table<String, String, Set<Vertex>> isolatedTargetVerticesForFields = HashBasedTable.create();
        Table<String, String, Set<Vertex>> isolatedSourceVerticesForFields = HashBasedTable.create();
        for (String type : SchemaGraphFactory.ALL_TYPES) {
            if (FIELD.equals(type)) {

                Stream<Vertex> sourceVerticesStream = sourceGraph.getVerticesByType(type).stream().filter(vertex -> !vertex.isBuiltInType());
                Stream<Vertex> targetVerticesStream = targetGraph.getVerticesByType(type).stream().filter(vertex -> !vertex.isBuiltInType());
                Map<String, ImmutableList<Vertex>> sourceFieldsByContainer = FpKit.groupingBy(sourceVerticesStream, v -> {
                    Vertex fieldsContainer = getFieldsContainerForField(v, sourceGraph);
                    return fieldsContainer.getType() + "-" + fieldsContainer.get("name");
                });
                Map<String, ImmutableList<Vertex>> targetFieldsByContainer = FpKit.groupingBy(targetVerticesStream, v -> {
                    Vertex fieldsContainer = getFieldsContainerForField(v, targetGraph);
                    return fieldsContainer.getType() + "-" + fieldsContainer.get("name");
                });

                List<String> deletedContainers = new ArrayList<>();
                List<String> insertedContainers = new ArrayList<>();
                List<String> sameContainers = new ArrayList<>();
                diffNamedList(sourceFieldsByContainer.keySet(), targetFieldsByContainer.keySet(), deletedContainers, insertedContainers, sameContainers);

                for (String sameContainer : sameContainers) {
                    int ix = sameContainer.indexOf("-");
                    String containerName = sameContainer.substring(ix + 1);
                    // we have this container is source and target
                    ImmutableList<Vertex> sourceVertices = sourceFieldsByContainer.get(sameContainer);
                    ImmutableList<Vertex> targetVertices = targetFieldsByContainer.get(sameContainer);

                    ArrayList<Vertex> deletedFields = new ArrayList<>();
                    ArrayList<Vertex> insertedFields = new ArrayList<>();
                    HashBiMap<Vertex, Vertex> sameFields = HashBiMap.create();
                    diffNamedList(sourceVertices, targetVertices, deletedFields, insertedFields, sameFields);

                    if (deletedFields.size() > insertedFields.size()) {
                        Set<Vertex> newTargetVertices = Vertex.newArtificialNodes(deletedFields.size() - insertedFields.size(), "target-artificial-" + type + "-");
                        for (Vertex deletedField : deletedFields) {
                            isolatedTargetVerticesForFields.put(containerName, deletedField.get("name"), newTargetVertices);
                        }
                    } else if (insertedFields.size() > deletedFields.size()) {
                        Set<Vertex> newSourceVertices = Vertex.newArtificialNodes(insertedFields.size() - deletedFields.size(), "source-artificial-" + type + "-");
                        for (Vertex insertedField : insertedFields) {
                            isolatedSourceVerticesForFields.put(containerName, insertedField.get("name"), newSourceVertices);
                        }
                    }
                }

                Set<Vertex> insertedFields = new LinkedHashSet<>();
                Set<Vertex> deletedFields = new LinkedHashSet<>();
                for (String insertedContainer : insertedContainers) {
                    insertedFields.addAll(targetFieldsByContainer.get(insertedContainer));
                }
                for (String deletedContainer : deletedContainers) {
                    deletedFields.addAll(sourceFieldsByContainer.get(deletedContainer));
                }
                if (deletedFields.size() > insertedFields.size()) {
                    isolatedTargetVertices.put(type, Vertex.newArtificialNodes(deletedFields.size() - insertedFields.size(), "target-artificial-FIELD-"));
                } else if (insertedFields.size() > deletedFields.size()) {
                    isolatedSourceVertices.put(type, Vertex.newArtificialNodes(insertedFields.size() - deletedFields.size(), "source-artificial-FIELD-"));
                }

            } else {
                Collection<Vertex> sourceVertices = sourceGraph.getVerticesByType(type).stream().filter(vertex -> !vertex.isBuiltInType()).collect(Collectors.toList());
                Collection<Vertex> targetVertices = targetGraph.getVerticesByType(type).stream().filter(vertex -> !vertex.isBuiltInType()).collect(Collectors.toList());
//                System.out.println("type: " + type + " source size: " + sourceVertices.size());
                if (sourceVertices.size() > targetVertices.size()) {
                    isolatedTargetVertices.put(type, Vertex.newArtificialNodes(sourceVertices.size() - targetVertices.size(), "target-artificial-" + type + "-"));
                } else if (targetVertices.size() > sourceVertices.size()) {
                    isolatedSourceVertices.put(type, Vertex.newArtificialNodes(targetVertices.size() - sourceVertices.size(), "source-artificial-" + type + "-"));
                }
            }
//            if (isNamedType(type)) {
//                ArrayList<Vertex> deleted = new ArrayList<>();
//                ArrayList<Vertex> inserted = new ArrayList<>();
//                HashBiMap<Vertex, Vertex> same = HashBiMap.create();
//                diffNamedList(sourceVertices, targetVertices, deleted, inserted, same);
//                System.out.println(" " + type + " deleted " + deleted.size() + " inserted" + inserted.size() + " same " + same.size());
//            }
        }

        for (Map.Entry<String, Set<Vertex>> entry : isolatedSourceVertices.entrySet()) {
            sourceGraph.addVertices(entry.getValue());
        }
        for (Map.Entry<String, Set<Vertex>> entry : isolatedTargetVertices.entrySet()) {
            targetGraph.addVertices(entry.getValue());
        }
        for (String containerName : isolatedSourceVerticesForFields.rowKeySet()) {
            Map<String, Set<Vertex>> row = isolatedSourceVerticesForFields.row(containerName);
            sourceGraph.addVertices(row.values().iterator().next());
        }
        for (String containerName : isolatedTargetVerticesForFields.rowKeySet()) {
            Map<String, Set<Vertex>> row = isolatedTargetVerticesForFields.row(containerName);
            targetGraph.addVertices(row.values().iterator().next());
        }

        Set<Vertex> isolatedBuiltInSourceVertices = new LinkedHashSet<>();
        Set<Vertex> isolatedBuiltInTargetVertices = new LinkedHashSet<>();
        // the only vertices left are built in types.
        if (sourceGraph.size() < targetGraph.size()) {
            isolatedBuiltInSourceVertices.addAll(sourceGraph.addIsolatedVertices(targetGraph.size() - sourceGraph.size(), "source-artificial-builtin-"));
        } else if (sourceGraph.size() > targetGraph.size()) {
            isolatedBuiltInTargetVertices.addAll(targetGraph.addIsolatedVertices(sourceGraph.size() - targetGraph.size(), "target-artificial-builtin-"));
        }
        assertTrue(sourceGraph.size() == targetGraph.size());
        IsolatedInfo isolatedInfo = new IsolatedInfo(isolatedSourceVertices, isolatedTargetVertices, isolatedBuiltInSourceVertices, isolatedBuiltInTargetVertices, isolatedSourceVerticesForFields, isolatedTargetVerticesForFields);

        int graphSize = sourceGraph.size();
        System.out.println("graph size: " + graphSize);

        if (sizeDiff != 0) {
            sortSourceGraph(sourceGraph, targetGraph);
        }

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
//            System.out.println((++counter) + " check entry at level " + mappingEntry.level + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost + " map " + getDebugMap(mappingEntry.partialMapping));
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
                        isolatedInfo
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

    private int totalWeightWithSomeEdges(SchemaGraph sourceGraph, Vertex
            vertex, List<Edge> edges, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
        if (vertex.isBuiltInType()) {
            return Integer.MIN_VALUE + 1;
        }
        if (vertex.isArtificialNode()) {
            return Integer.MIN_VALUE + 2;
        }
        return vertexWeights.get(vertex) + edges.stream().mapToInt(edgesWeights::get).sum();
    }

    private int totalWeightWithAdjacentEdges(SchemaGraph sourceGraph, Vertex
            vertex, Map<Vertex, Integer> vertexWeights, Map<Edge, Integer> edgesWeights) {
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
                                  AtomicReference<Mapping> bestFullMapping,
                                  AtomicReference<List<EditOperation>> bestEdit,
                                  SchemaGraph sourceGraph,
                                  SchemaGraph targetGraph,
                                  IsolatedInfo isolatedInfo

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


        List<Callable<Void>> callables = new ArrayList<>();
        // costMatrix[0] is the row for  v_i
        for (int i = level - 1; i < sourceList.size(); i++) {
            Vertex v = sourceList.get(i);
            int finalI = i;
            Callable<Void> callable = () -> {
                try {
                    int j = 0;
                    for (Vertex u : availableTargetVertices) {
                        double cost = calcLowerBoundMappingCost(v, u, sourceGraph, targetGraph, partialMapping.getSources(), partialMappingSourceSet, partialMapping.getTargets(), partialMappingTargetSet, isolatedInfo);
                        costMatrix[finalI - level + 1].set(j, cost);
                        costMatrixCopy[finalI - level + 1].set(j, cost);
//                        if (v.getType().equals("AppliedArgument") && ((String) v.get("value")).contains("jira-software") && cost != Integer.MAX_VALUE) {
//                            System.out.println((finalI - level + 1) + ", " + j + ": cost: " + cost + " for " + v + " to " + u);
//                        }
                        j++;
                    }
                    return null;
                } catch (Throwable t) {
                    t.printStackTrace();
                    return null;
                }
            };
            callables.add(callable);
//            callable.call();
        }
        forkJoinPool.invokeAll(callables);
        forkJoinPool.awaitTermination(10000, TimeUnit.DAYS);

        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm(costMatrix);

        int[] assignments = hungarianAlgorithm.execute();
        int editorialCostForMapping = editorialCostForMapping(partialMapping, sourceGraph, targetGraph, new ArrayList<>());
        double costMatrixSum = getCostMatrixSum(costMatrixCopy, assignments);


        double lowerBoundForPartialMappingSibling = editorialCostForMapping + costMatrixSum;
        int v_i_target_IndexSibling = assignments[0];
        Vertex bestExtensionTargetVertexSibling = availableTargetVertices.get(v_i_target_IndexSibling);
        Mapping newMappingSibling = partialMapping.extendMapping(v_i, bestExtensionTargetVertexSibling);


        if (lowerBoundForPartialMappingSibling >= upperBound.doubleValue()) {
            return;
        }
        MappingEntry newMappingEntry = new MappingEntry(newMappingSibling, level, lowerBoundForPartialMappingSibling);
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
        }
    }


    private double getCostMatrixSum(AtomicDoubleArray[] costMatrix, int[] assignments) {
        double costMatrixSum = 0;
        for (int i = 0; i < assignments.length; i++) {
            costMatrixSum += costMatrix[i].get(assignments[i]);
        }
        return costMatrixSum;
    }

//    private Vertex findMatchingVertex(Vertex v_i, List<Vertex> availableTargetVertices, SchemaGraph
//            sourceGraph, SchemaGraph targetGraph) {
//        String viType = v_i.getType();
//        HashMultiset<String> viAdjacentEdges = HashMultiset.create(sourceGraph.getAdjacentEdges(v_i).stream().map(edge -> edge.getLabel()).collect(Collectors.toList()));
//        if (viType.equals(SchemaGraphFactory.OBJECT) || viType.equals(INTERFACE) || viType.equals(UNION) || viType.equals(INPUT_OBJECT)) {
//            for (Vertex targetVertex : availableTargetVertices) {
//                if (v_i.isEqualTo(targetVertex)) {
//                    // check if edges are the same
//                    HashMultiset<String> adjacentEdges = HashMultiset.create(targetGraph.getAdjacentEdges(targetVertex).stream().map(Edge::getLabel).collect(Collectors.toList()));
//                    if (viAdjacentEdges.equals(adjacentEdges)) {
//                        return targetVertex;
//                    }
//                }
//
//            }
//        }
//        return null;
//    }

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
    private int editorialCostForMapping(Mapping partialOrFullMapping, SchemaGraph sourceGraph, SchemaGraph
            targetGraph, List<EditOperation> editOperationsResult) {
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


    private Map<Vertex, Vertex> forcedMatchingCache = synchronizedMap(new LinkedHashMap<>());

    static class IsolatedInfo {
        Map<String, Set<Vertex>> isolatedSourceVertices;
        Map<String, Set<Vertex>> isolatedTargetVertices;
        Set<Vertex> isolatedBuiltInSourceVertices;
        Set<Vertex> isolatedBuiltInTargetVertices;
        Table<String, String, Set<Vertex>> isolatedSourceVerticesForFields;
        Table<String, String, Set<Vertex>> isolatedTargetVerticesForFields;

        public IsolatedInfo(Map<String, Set<Vertex>> isolatedSourceVertices, Map<String, Set<Vertex>> isolatedTargetVertices, Set<Vertex> isolatedBuiltInSourceVertices,
                            Set<Vertex> isolatedBuiltInTargetVertices,
                            Table<String, String, Set<Vertex>> isolatedSourceVerticesForFields,
                            Table<String, String, Set<Vertex>> isolatedTargetVerticesForFields) {
            this.isolatedSourceVertices = isolatedSourceVertices;
            this.isolatedTargetVertices = isolatedTargetVertices;
            this.isolatedBuiltInSourceVertices = isolatedBuiltInSourceVertices;
            this.isolatedBuiltInTargetVertices = isolatedBuiltInTargetVertices;
            this.isolatedSourceVerticesForFields = isolatedSourceVerticesForFields;
            this.isolatedTargetVerticesForFields = isolatedTargetVerticesForFields;
        }
    }

    private boolean isMappingPossible(Vertex v,
                                      Vertex u,
                                      SchemaGraph sourceGraph,
                                      SchemaGraph targetGraph,
                                      Set<Vertex> partialMappingTargetSet,
                                      IsolatedInfo isolatedInfo
    ) {

        Vertex forcedMatch = forcedMatchingCache.get(v);
        if (forcedMatch != null) {
            return forcedMatch == u;
        }
        if (v.isArtificialNode() && u.isArtificialNode()) {
            return false;
        }
        Map<String, Set<Vertex>> isolatedSourceVertices = isolatedInfo.isolatedSourceVertices;
        Map<String, Set<Vertex>> isolatedTargetVertices = isolatedInfo.isolatedTargetVertices;
        Set<Vertex> isolatedBuiltInSourceVertices = isolatedInfo.isolatedBuiltInSourceVertices;
        Set<Vertex> isolatedBuiltInTargetVertices = isolatedInfo.isolatedBuiltInTargetVertices;
        Table<String, String, Set<Vertex>> isolatedSourceVerticesForFields = isolatedInfo.isolatedSourceVerticesForFields;
        Table<String, String, Set<Vertex>> isolatedTargetVerticesForFields = isolatedInfo.isolatedTargetVerticesForFields;

        if (v.isArtificialNode()) {
            if (u.isBuiltInType()) {
                return isolatedBuiltInSourceVertices.contains(v);
            } else {
                if (u.getType().equals(FIELD)) {
                    Vertex fieldsContainer = getFieldsContainerForField(u, targetGraph);
                    String containerName = fieldsContainer.get("name");
                    String fieldName = u.get("name");
                    if (isolatedSourceVerticesForFields.contains(containerName, fieldName)) {
                        return isolatedSourceVerticesForFields.get(containerName, fieldName).contains(v);
                    }
                }
                return isolatedSourceVertices.getOrDefault(u.getType(), emptySet()).contains(v);
            }
        }
        if (u.isArtificialNode()) {
            if (v.isBuiltInType()) {
                return isolatedBuiltInTargetVertices.contains(u);
            } else {
                if (v.getType().equals(FIELD)) {
                    Vertex fieldsContainer = getFieldsContainerForField(v, sourceGraph);
                    String containerName = fieldsContainer.get("name");
                    String fieldName = u.get("name");
                    if (isolatedTargetVerticesForFields.contains(containerName, fieldName)) {
                        return isolatedTargetVerticesForFields.get(containerName, fieldName).contains(u);
                    }
                }
                return isolatedTargetVertices.getOrDefault(v.getType(), emptySet()).contains(u);
            }
        }
        // the types of the vertices need to match: we don't allow to change the type
        if (!v.getType().equals(u.getType())) {
            return false;
        }
        Boolean result = checkNamedTypes(v, u, targetGraph);
        if (result != null) {
            return result;
        }
        result = checkNamedTypes(u, v, sourceGraph);
        if (result != null) {
            return result;
        }
        result = checkSpecificTypes(v, u, sourceGraph, targetGraph);
        if (result != null) {
            return result;
        }
        result = checkSpecificTypes(u, v, targetGraph, sourceGraph);
        if (result != null) {
            return result;
        }

        return true;
    }

    private Boolean checkSpecificTypes(Vertex v, Vertex u, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex matchingTargetVertex = findMatchingTargetVertex(v, sourceGraph, targetGraph);
        if (matchingTargetVertex != null) {
            forcedMatchingCache.put(v, matchingTargetVertex);
            forcedMatchingCache.put(matchingTargetVertex, v);
            return u == matchingTargetVertex;
        }
        return null;

//        if (DIRECTIVE.equals(v.getType())) {
//            Vertex targetVertex = targetGraph.getDirective(v.get("name"));
//            if (targetVertex != null) {
//                forcedMatchingCache.put(v, targetVertex);
//                forcedMatchingCache.put(targetVertex, v);
//                return u == targetVertex;
//            }
//        }
//
//        if (DUMMY_TYPE_VERTEX.equals(v.getType())) {
//            List<Vertex> adjacentVertices = sourceGraph.getAdjacentVertices(v);
//            for (Vertex vertex : adjacentVertices) {
//                if (vertex.getType().equals(FIELD)) {
//                    Vertex matchingTargetField = findMatchingTargetField(vertex, sourceGraph, targetGraph);
//                    if (matchingTargetField != null) {
//                        Vertex dummyTypeVertex = getDummyTypeVertex(matchingTargetField, targetGraph);
//                        forcedMatchingCache.put(v, dummyTypeVertex);
//                        forcedMatchingCache.put(dummyTypeVertex, v);
//                        return u == dummyTypeVertex;
//                    }
//                } else if (vertex.getType().equals(INPUT_FIELD)) {
//                    Vertex matchingTargetInputField = findMatchingTargetInputField(vertex, sourceGraph, targetGraph);
//                    if (matchingTargetInputField != null) {
//                        Vertex dummyTypeVertex = getDummyTypeVertex(matchingTargetInputField, targetGraph);
//                        forcedMatchingCache.put(v, dummyTypeVertex);
//                        forcedMatchingCache.put(dummyTypeVertex, v);
//                        return u == dummyTypeVertex;
//                    }
//                }
//            }
//        }
//        if (INPUT_FIELD.equals(v.getType())) {
//            Vertex matchingTargetInputField = findMatchingTargetInputField(v, sourceGraph, targetGraph);
//            if (matchingTargetInputField != null) {
//                forcedMatchingCache.put(v, matchingTargetInputField);
//                forcedMatchingCache.put(matchingTargetInputField, v);
//                return u == matchingTargetInputField;
//            }
//        }
//        if (FIELD.equals(v.getType())) {
//            Vertex matchingTargetField = findMatchingTargetField(v, sourceGraph, targetGraph);
//            if (matchingTargetField != null) {
//                forcedMatchingCache.put(v, matchingTargetField);
//                forcedMatchingCache.put(matchingTargetField, v);
//                return u == matchingTargetField;
//            }
//        }
//        if (ENUM_VALUE.equals(v.getType())) {
//            Vertex matchingTargetEnumValue = findMatchingEnumValue(v, sourceGraph, targetGraph);
//            if (matchingTargetEnumValue != null) {
//                forcedMatchingCache.put(v, matchingTargetEnumValue);
//                forcedMatchingCache.put(matchingTargetEnumValue, v);
//                return u == matchingTargetEnumValue;
//            }
//        }
//        if (ARGUMENT.equals(v.getType())) {
//            Vertex matchingTargetArgument = findMatchingTargetArgument(v, sourceGraph, targetGraph);
//            if (matchingTargetArgument != null) {
//                forcedMatchingCache.put(v, matchingTargetArgument);
//                forcedMatchingCache.put(matchingTargetArgument, v);
//                return u == matchingTargetArgument;
//            }
//        }
//        if (APPLIED_ARGUMENT.equals(v.getType())) {
//            Vertex matchingTargetArgument = findMatchingTargetArgument(v, sourceGraph, targetGraph);
//            if (matchingTargetArgument != null) {
//                forcedMatchingCache.put(v, matchingTargetArgument);
//                forcedMatchingCache.put(matchingTargetArgument, v);
//                return u == matchingTargetArgument;
//            }
//        }
//        return null;
    }

    private Vertex findMatchingTargetVertex(Vertex v, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        if (isNamedType(v.getType())) {
            Vertex targetVertex = targetGraph.getType(v.get("name"));
            // the type of the target vertex must match v
            return targetVertex != null && targetVertex.getType().equals(v.getType()) ? targetVertex : null;
        }
        if (DIRECTIVE.equals(v.getType())) {
            return targetGraph.getDirective(v.get("name"));
        }

        if (DUMMY_TYPE_VERTEX.equals(v.getType())) {
            List<Vertex> adjacentVertices = sourceGraph.getAdjacentVertices(v);
            for (Vertex vertex : adjacentVertices) {
                if (vertex.getType().equals(FIELD)) {
                    Vertex matchingTargetField = findMatchingTargetField(vertex, sourceGraph, targetGraph);
                    if (matchingTargetField != null) {
                        return getDummyTypeVertex(matchingTargetField, targetGraph);
                    }
                } else if (vertex.getType().equals(INPUT_FIELD)) {
                    Vertex matchingTargetInputField = findMatchingTargetInputField(vertex, sourceGraph, targetGraph);
                    if (matchingTargetInputField != null) {
                        return getDummyTypeVertex(matchingTargetInputField, targetGraph);
                    }
                }
            }
            return null;
        }
        if (INPUT_FIELD.equals(v.getType())) {
            Vertex matchingTargetInputField = findMatchingTargetInputField(v, sourceGraph, targetGraph);
            return matchingTargetInputField;
        }
        if (FIELD.equals(v.getType())) {
            Vertex matchingTargetField = findMatchingTargetField(v, sourceGraph, targetGraph);
            return matchingTargetField;
        }
        if (ENUM_VALUE.equals(v.getType())) {
            Vertex matchingTargetEnumValue = findMatchingEnumValue(v, sourceGraph, targetGraph);
            return matchingTargetEnumValue;
        }
        if (ARGUMENT.equals(v.getType())) {
            Vertex matchingTargetArgument = findMatchingTargetArgument(v, sourceGraph, targetGraph);
            return matchingTargetArgument;
        }

        if (APPLIED_DIRECTIVE.equals(v.getType())) {
            return findMatchingAppliedDirective(v, sourceGraph, targetGraph);
        }
        if (APPLIED_ARGUMENT.equals(v.getType())) {
            return findMatchingAppliedArgument(v, sourceGraph, targetGraph);
        }

        return Assert.assertShouldNeverHappen();
    }

    private Boolean checkNamedTypes(Vertex v, Vertex u, SchemaGraph targetGraph) {
        if (isNamedType(v.getType())) {
            Vertex targetVertex = targetGraph.getType(v.get("name"));
            if (targetVertex != null && Objects.equals(v.getType(), targetVertex.getType())) {
                forcedMatchingCache.put(v, targetVertex);
                forcedMatchingCache.put(targetVertex, v);
                return u == targetVertex;
            }
        }
        return null;
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

    private Vertex findMatchingTargetArgument(Vertex argument, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex fieldOrDirective = getFieldOrDirectiveForArgument(argument, sourceGraph);
        if (FIELD.equals(fieldOrDirective.getType())) {
            Vertex matchingTargetField = findMatchingTargetField(fieldOrDirective, sourceGraph, targetGraph);
            if (matchingTargetField != null) {
                return getArgumentForFieldOrDirective(matchingTargetField, argument.get("name"), targetGraph);
            }
        } else {
            // we have an Argument for a Directive
            Vertex matchingDirective = targetGraph.getDirective(fieldOrDirective.get("name"));
            if (matchingDirective != null) {
                return getArgumentForFieldOrDirective(matchingDirective, argument.get("name"), targetGraph);
            }
        }
        return null;
    }

    private Vertex findMatchingAppliedDirective(Vertex appliedDirective, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex targetDirectiveWithSameName = targetGraph.getDirective(appliedDirective.get("name"));
        if (targetDirectiveWithSameName == null) {
            return null;
        }
        List<Vertex> adjacentVertices = sourceGraph.getAdjacentVertices(appliedDirective, vertex -> !vertex.getType().equals(APPLIED_ARGUMENT));
        assertTrue(adjacentVertices.size() == 1);
        Vertex elementDirectiveAppliedTo = adjacentVertices.get(0);
        //TODO: handle repeatable directives
        Vertex targetMatchingAppliedTo = findMatchingTargetVertex(elementDirectiveAppliedTo, sourceGraph, targetGraph);
        if (targetMatchingAppliedTo == null) {
            return null;
        }
        List<Vertex> targetAppliedDirectives = targetGraph.getAdjacentVertices(targetMatchingAppliedTo, vertex -> vertex.getType().equals(APPLIED_DIRECTIVE) && vertex.get("name").equals(appliedDirective.get("name")));
        return targetAppliedDirectives.size() == 0 ? null : targetAppliedDirectives.get(0);
    }

    private Vertex findMatchingAppliedArgument(Vertex appliedArgument, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        List<Vertex> appliedDirectives = sourceGraph.getAdjacentVertices(appliedArgument, vertex -> vertex.getType().equals(APPLIED_DIRECTIVE));
        assertTrue(appliedDirectives.size() == 1);
        Vertex appliedDirective = appliedDirectives.get(0);
        Vertex matchingAppliedDirective = findMatchingAppliedDirective(appliedDirective, sourceGraph, targetGraph);
        if (matchingAppliedDirective == null) {
            return null;
        }
        List<Vertex> matchingAppliedArguments = targetGraph.getAdjacentVertices(matchingAppliedDirective, vertex -> vertex.getType().equals(APPLIED_ARGUMENT) && vertex.get("name").equals(appliedArgument.get("name")));
        assertTrue(matchingAppliedArguments.size() <= 1);
        return matchingAppliedArguments.size() == 0 ? null : matchingAppliedArguments.get(0);

    }

    private Vertex findMatchingTargetField(Vertex field, SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        Vertex sourceFieldsContainer = getFieldsContainerForField(field, sourceGraph);
        Vertex targetFieldsContainerWithSameName = targetGraph.getType(sourceFieldsContainer.get("name"));
        if (targetFieldsContainerWithSameName != null && targetFieldsContainerWithSameName.getType().equals(sourceFieldsContainer.getType())) {
            Vertex matchingField = getFieldForContainer(targetFieldsContainerWithSameName, field.get("name"), targetGraph);
            return matchingField;
        }
        return null;
    }

    private Vertex getInputField(Vertex inputObject, String fieldName, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(inputObject, v -> v.getType().equals(INPUT_FIELD) && fieldName.equals(v.get("name")));
        assertTrue(adjacentVertices.size() <= 1);
        return adjacentVertices.size() == 0 ? null : adjacentVertices.get(0);
    }

    private Vertex getFieldForContainer(Vertex fieldsContainer, String fieldName, SchemaGraph schemaGraph) {
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

    private Vertex getFieldOrDirectiveForArgument(Vertex argument, SchemaGraph schemaGraph) {
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(argument, vertex -> vertex.getType().equals(FIELD) || vertex.getType().equals(DIRECTIVE));
        assertTrue(adjacentVertices.size() == 1, () -> format("No field or directive found for %s", argument));
        return adjacentVertices.get(0);
    }

    private Vertex getArgumentForFieldOrDirective(Vertex fieldOrDirective, String argumentName, SchemaGraph schemaGraph) {
        assertTrue(DIRECTIVE.equals(fieldOrDirective.getType()) || FIELD.equals(fieldOrDirective.getType()));
        List<Vertex> adjacentVertices = schemaGraph.getAdjacentVertices(fieldOrDirective, vertex -> vertex.getType().equals(ARGUMENT) && vertex.get("name").equals(argumentName));
        assertTrue(adjacentVertices.size() <= 1);
        return adjacentVertices.size() == 0 ? null : adjacentVertices.get(0);
    }

    private Vertex getFieldsContainerForField(Vertex field, SchemaGraph schemaGraph) {
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
                                             Set<Vertex> partialMappingTargetSet,
                                             IsolatedInfo isolatedInfo

    ) {
        if (!isMappingPossible(v, u, sourceGraph, targetGraph, partialMappingTargetSet, isolatedInfo)) {
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

    AtomicInteger zeroResult = new AtomicInteger();

}
