package graphql.schema.diffing;

import java.util.List;

public class EditorialCostForMapping {

    /**
     * a mapping introduces a subgraph consisting of all vertices and all edges between these vertices
     */
    public static int editorialCostForMapping(Mapping mapping, // can be a partial mapping
                                              SchemaGraph sourceGraph, // the whole graph
                                              SchemaGraph targetGraph, // the whole graph
                                              List<EditOperation> editOperationsResult) {
        int cost = 0;
        for (int i = 0; i < mapping.size(); i++) {
            Vertex sourceVertex = mapping.getSource(i);
            Vertex targetVertex = mapping.getTarget(i);
            // Vertex changing (relabeling)
            boolean equalNodes = sourceVertex.getType().equals(targetVertex.getType()) && sourceVertex.getProperties().equals(targetVertex.getProperties());
            if (!equalNodes) {
                if (sourceVertex.isIsolated()) {
                    editOperationsResult.add(EditOperation.insertVertex("Insert" + targetVertex, sourceVertex, targetVertex));
                } else if (targetVertex.isIsolated()) {
                    editOperationsResult.add(EditOperation.deleteVertex("Delete " + sourceVertex, sourceVertex, targetVertex));
                } else {
                    editOperationsResult.add(EditOperation.changeVertex("Change " + sourceVertex + " to " + targetVertex, sourceVertex, targetVertex));
                }
                cost++;
            }
        }
        List<Edge> edges = sourceGraph.getEdges();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            // only edges relevant to the subgraph
            if (!mapping.containsSource(sourceEdge.getFrom()) || !mapping.containsSource(sourceEdge.getTo())) {
                continue;
            }
            Vertex targetFrom = mapping.getTarget(sourceEdge.getFrom());
            Vertex targetTo = mapping.getTarget(sourceEdge.getTo());
            Edge targetEdge = targetGraph.getEdge(targetFrom, targetTo);
            if (targetEdge == null) {
                editOperationsResult.add(EditOperation.deleteEdge("Delete edge " + sourceEdge, sourceEdge));
                cost++;
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                editOperationsResult.add(EditOperation.changeEdge("Change " + sourceEdge + " to " + targetEdge, sourceEdge, targetEdge));
                cost++;
            }
        }

        //TODO: iterates over all edges in the target Graph
        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            if (!mapping.containsTarget(targetEdge.getFrom()) || !mapping.containsTarget(targetEdge.getTo())) {
                continue;
            }
            Vertex sourceFrom = mapping.getSource(targetEdge.getFrom());
            Vertex sourceTo = mapping.getSource(targetEdge.getTo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                editOperationsResult.add(EditOperation.insertEdge("Insert edge " + targetEdge, targetEdge));
                cost++;
            }
        }
        return cost;
    }


}
