//package graphql.schema.diffing;
//
//import graphql.Internal;
//
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Internal
//public class SortSourceGraph {
//
//    public static void sortSourceGraph(SchemaGraph sourceGraph, SchemaGraph targetGraph, PossibleMappingsCalculator.PossibleMappings possibleMappings) {
////        // we sort descending by number of possible target vertices
////        Collections.sort(sourceGraph.getVertices(), (v1, v2) ->
////
////        {
////
////            int v2Count = v2.isBuiltInType() ? -1 : (v2.isIsolated() ? 0 : isolatedVertices.possibleMappings.get(v2).size());
////            int v1Count = v1.isBuiltInType() ? -1 : (v1.isIsolated() ? 0 : isolatedVertices.possibleMappings.get(v1).size());
////            return Integer.compare(v2Count, v1Count);
////        });
////
////        for (Vertex vertex : sourceGraph.getVertices()) {
////            System.out.println("c: " + isolatedVertices.possibleMappings.get(vertex).size() + " v: " + vertex);
////        }
//
////
////
////        // how often does each source edge (based on the label) appear in target graph
//        Map<String, AtomicInteger> targetLabelCount = new LinkedHashMap<>();
//        for (Edge targetEdge : targetGraph.getEdges()) {
//            targetLabelCount.computeIfAbsent(targetEdge.getLabel(), __ -> new AtomicInteger()).incrementAndGet();
//        }
//        // how often does each source vertex (based on the data) appear in the target graph
//        Map<Vertex.VertexData, AtomicInteger> targetVertexDataCount = new LinkedHashMap<>();
//        for (Vertex targetVertex : targetGraph.getVertices()) {
//            targetVertexDataCount.computeIfAbsent(targetVertex.toData(), __ -> new AtomicInteger()).incrementAndGet();
//        }
//
//        // an infrequency weight is 1 - count in target. Meaning the higher the
//        // value, the smaller the count, the less frequent it.
//        // Higher Infrequency => more unique is the vertex/label
//        Map<Vertex, Integer> vertexInfrequencyWeights = new LinkedHashMap<>();
//        Map<Edge, Integer> edgesInfrequencyWeights = new LinkedHashMap<>();
//        for (Vertex vertex : sourceGraph.getVertices()) {
//            vertexInfrequencyWeights.put(vertex, 1 - targetVertexDataCount.getOrDefault(vertex.toData(), new AtomicInteger()).get());
//        }
//        for (Edge edge : sourceGraph.getEdges()) {
//            edgesInfrequencyWeights.put(edge, 1 - targetLabelCount.getOrDefault(edge.getLabel(), new AtomicInteger()).get());
//        }
//
//        /**
//         * vertices are sorted by increasing frequency/decreasing infrequency/decreasing uniqueness
//         * we start with the most unique/least frequent/most infrequent and add incrementally the next most infrequent.
//         */
//
//        //TODO: improve this: this is doing to much: we just want the max infrequent vertex, not all sorted
//        ArrayList<Vertex> nextCandidates = new ArrayList<>(sourceGraph.getVertices());
//        nextCandidates.sort(Comparator.comparingInt(o -> totalInfrequencyWeightWithAdjacentEdges(sourceGraph, o, vertexInfrequencyWeights, edgesInfrequencyWeights)));
//
//        Vertex curVertex = nextCandidates.get(nextCandidates.size() - 1);
//        nextCandidates.remove(nextCandidates.size() - 1);
//
//        List<Vertex> result = new ArrayList<>();
//        result.add(curVertex);
//        while (nextCandidates.size() > 0) { Vertex nextOne = null;
//            int curMaxWeight = Integer.MIN_VALUE;
//            int index = 0;
//            int nextOneIndex = -1;
//
//            // which ones of the candidates has the highest infrequency weight relatively to the current result set of vertices
//            for (Vertex candidate : nextCandidates) {
//                List<Edge> allAdjacentEdges = sourceGraph.getAllAdjacentEdges(result, candidate);
//                int totalWeight = totalInfrequencyWeightWithSomeEdges(candidate, allAdjacentEdges, vertexInfrequencyWeights, edgesInfrequencyWeights);
//                if (totalWeight > curMaxWeight) {
//                    nextOne = candidate;
//                    nextOneIndex = index;
//                    curMaxWeight = totalWeight;
//                }
//                index++;
//            }
//            result.add(nextOne);
//            nextCandidates.remove(nextOneIndex);
//        }
//        sourceGraph.setVertices(result);
//    }
//
//
//    private static int totalInfrequencyWeightWithSomeEdges(Vertex vertex,
//                                                    List<Edge> edges,
//                                                    Map<Vertex, Integer> vertexInfrequencyWeights,
//                                                    Map<Edge, Integer> edgesInfrequencyWeights) {
//        if (vertex.isBuiltInType()) {
//            return Integer.MIN_VALUE + 1;
//        }
//        if (vertex.isIsolated()) {
//            return Integer.MIN_VALUE + 2;
//        }
//        return vertexInfrequencyWeights.get(vertex) + edges.stream().mapToInt(edgesInfrequencyWeights::get).sum();
//    }
//
//    private static int totalInfrequencyWeightWithAdjacentEdges(SchemaGraph sourceGraph,
//                                                        Vertex vertex,
//                                                        Map<Vertex, Integer> vertexInfrequencyWeights,
//                                                        Map<Edge, Integer> edgesInfrequencyWeights) {
//        if (vertex.isBuiltInType()) {
//            return Integer.MIN_VALUE + 1;
//        }
//        if (vertex.isIsolated()) {
//            return Integer.MIN_VALUE + 2;
//        }
//        List<Edge> adjacentEdges = sourceGraph.getAdjacentEdges(vertex);
//        return vertexInfrequencyWeights.get(vertex) + adjacentEdges.stream().mapToInt(edgesInfrequencyWeights::get).sum();
//    }
//
//    private int infrequencyWeightForVertex(Vertex sourceVertex, SchemaGraph targetGraph) {
//        int count = 0;
//        for (Vertex targetVertex : targetGraph.getVertices()) {
//            if (sourceVertex.isEqualTo(targetVertex)) {
//                count++;
//            }
//        }
//        return 1 - count;
//    }
//
//    private int infrequencyWeightForEdge(Edge sourceEdge, SchemaGraph targetGraph) {
//        int count = 0;
//        for (Edge targetEdge : targetGraph.getEdges()) {
//            if (sourceEdge.isEqualTo(targetEdge)) {
//                count++;
//            }
//        }
//        return 1 - count;
//    }
//
//
//
//}
