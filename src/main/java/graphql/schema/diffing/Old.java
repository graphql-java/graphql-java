//package graphql.schema.diffing;
//
//public class Old {
//}

/*
        if (oldVersion) {

                while (!queue.isEmpty()) {
                MappingEntry mappingEntry = queue.poll();
                System.out.println((++counter) + " entry at level " + mappingEntry.level + " queue size: " + queue.size() + " lower bound " + mappingEntry.lowerBoundCost + " map " + getDebugMap(mappingEntry.partialMapping));
                if (mappingEntry.lowerBoundCost >= upperBoundCost.doubleValue()) {
//                System.out.println("skipping!");
                continue;
                }
                // generate sibling
                if (mappingEntry.level > 0 && mappingEntry.candidates.size() > 0) {
                // we need to remove the last mapping
                Mapping parentMapping = mappingEntry.partialMapping.removeLastElement();
                System.out.println("generate sibling");
                genNextMapping(parentMapping, mappingEntry.level, mappingEntry.candidates, queue, upperBoundCost, bestFullMapping, bestEdit, sourceGraph, targetGraph);
                }
                // generate children
                if (mappingEntry.level < graphSize) {
        // candidates are the vertices in target, of which are not used yet in partialMapping
        Set<Vertex> childCandidates = new LinkedHashSet<>(targetGraph.getVertices());
        childCandidates.removeAll(mappingEntry.partialMapping.getTargets());
        System.out.println("generate child");
        genNextMapping(mappingEntry.partialMapping, mappingEntry.level + 1, childCandidates, queue, upperBoundCost, bestFullMapping, bestEdit, sourceGraph, targetGraph);
        }
        }
        } else {


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
                }
                j++;
            }
        }
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
                System.out.println("setting new best edit at level " + level + " with size " + editOperations.size() + " at level " + level);
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


*/