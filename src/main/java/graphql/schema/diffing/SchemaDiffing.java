package graphql.schema.diffing;

import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.EditorialCostForMapping.editorialCostForMapping;

public class SchemaDiffing {


    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);

    }

    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        int sizeDiff = targetGraph.size() - sourceGraph.size();
        System.out.println("graph diff: " + sizeDiff);
        FillupIsolatedVertices fillupIsolatedVertices = new FillupIsolatedVertices(sourceGraph, targetGraph);
        fillupIsolatedVertices.ensureGraphAreSameSize();
        FillupIsolatedVertices.IsolatedVertices isolatedVertices = fillupIsolatedVertices.isolatedVertices;

        assertTrue(sourceGraph.size() == targetGraph.size());
//        if (sizeDiff != 0) {
//            SortSourceGraph.sortSourceGraph(sourceGraph, targetGraph, isolatedVertices);
//        }
        Mapping fixedMappings = isolatedVertices.mapping;
        System.out.println("fixed mappings: " + fixedMappings.size() + " vs " + sourceGraph.size());
        if (fixedMappings.size() == sourceGraph.size()) {
            ArrayList<EditOperation> result = new ArrayList<>();
            editorialCostForMapping(fixedMappings, sourceGraph, targetGraph, result);
            return result;
        }
        DiffImpl diffImpl = new DiffImpl(sourceGraph, targetGraph, isolatedVertices);
        List<Vertex> nonMappedSource = new ArrayList<>(sourceGraph.getVertices());
        nonMappedSource.removeAll(fixedMappings.getSources());

        List<Vertex> nonMappedTarget = new ArrayList<>(targetGraph.getVertices());
        nonMappedTarget.removeAll(fixedMappings.getTargets());

        sortListBasedOnPossibleMapping(nonMappedSource, isolatedVertices);

        // the non mapped vertices go to the end
        List<Vertex> sourceVertices = new ArrayList<>();
        sourceVertices.addAll(fixedMappings.getSources());
        sourceVertices.addAll(nonMappedSource);

        List<Vertex> targetGraphVertices = new ArrayList<>();
        targetGraphVertices.addAll(fixedMappings.getTargets());
        targetGraphVertices.addAll(nonMappedTarget);


        List<EditOperation> editOperations = diffImpl.diffImpl(fixedMappings, sourceVertices, targetGraphVertices);
        return editOperations;
    }

    private void sortListBasedOnPossibleMapping(List<Vertex> sourceVertices, FillupIsolatedVertices.IsolatedVertices isolatedVertices) {
        Collections.sort(sourceVertices, (v1, v2) ->
        {
            int v2Count = isolatedVertices.possibleMappings.get(v2).size();
            int v1Count = isolatedVertices.possibleMappings.get(v1).size();
            return Integer.compare(v2Count, v1Count);
        });

//        for (Vertex vertex : sourceGraph.getVertices()) {
//            System.out.println("c: " + isolatedVertices.possibleMappings.get(vertex).size() + " v: " + vertex);
//        }
    }


    private List<EditOperation> calcEdgeOperations(Mapping mapping) {
        List<Edge> edges = sourceGraph.getEdges();
        List<EditOperation> result = new ArrayList<>();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            Vertex target1 = mapping.getTarget(sourceEdge.getOne());
            Vertex target2 = mapping.getTarget(sourceEdge.getTwo());
            Edge targetEdge = targetGraph.getEdge(target1, target2);
            if (targetEdge == null) {
                result.add(EditOperation.deleteEdge("Delete edge " + sourceEdge, sourceEdge));
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                result.add(EditOperation.changeEdge("Change " + sourceEdge + " to " + targetEdge, sourceEdge, targetEdge));
            }
        }

        //TODO: iterates over all edges in the target Graph
        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            Vertex sourceFrom = mapping.getSource(targetEdge.getOne());
            Vertex sourceTo = mapping.getSource(targetEdge.getTwo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                result.add(EditOperation.insertEdge("Insert edge " + targetEdge, targetEdge));
            }
        }
        return result;
    }


//        List<String> debugMap = getDebugMap(bestFullMapping.get());
//        for (String debugLine : debugMap) {
//            System.out.println(debugLine);
//        }
//        System.out.println("edit : " + bestEdit);
//        for (EditOperation editOperation : bestEdit.get()) {
//            System.out.println(editOperation);
//        }
//    private List<EditOperation> diffImplImpl() {

//    private void logUnmappable(AtomicDoubleArray[] costMatrix, int[] assignments, List<Vertex> sourceList, ArrayList<Vertex> availableTargetVertices, int level) {
//        for (int i = 0; i < assignments.length; i++) {
//            double value = costMatrix[i].get(assignments[i]);
//            if (value >= Integer.MAX_VALUE) {
//                System.out.println("i " + i + " can't mapped");
//                Vertex v = sourceList.get(i + level - 1);
//                Vertex u = availableTargetVertices.get(assignments[i]);
//                System.out.println("from " + v + " to " + u);
//            }
//        }
//    }
//
//    private List<String> getDebugMap(Mapping mapping) {
//        List<String> result = new ArrayList<>();
////        if (mapping.size() > 0) {
////            result.add(mapping.getSource(mapping.size() - 1).getType() + " -> " + mapping.getTarget(mapping.size() - 1).getType());
////        }
//        for (Map.Entry<Vertex, Vertex> entry : mapping.getMap().entrySet()) {
////            if (!entry.getKey().getType().equals(entry.getValue().getType())) {
////                result.add(entry.getKey().getType() + "->" + entry.getValue().getType());
////            }
//            result.add(entry.getKey().getDebugName() + "->" + entry.getValue().getDebugName());
//        }
//        return result;
//    }
//
//    // minimum number of edit operations for a full mapping
//

}
