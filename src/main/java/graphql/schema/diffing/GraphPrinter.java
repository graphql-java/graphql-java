package graphql.schema.diffing;

import graphql.schema.diffing.dot.Dotfile;

public class GraphPrinter {

    public static String print(SchemaGraph schemaGraph) {
        Dotfile dotfile = new Dotfile();
        for (Vertex vertex : schemaGraph.getVertices()) {
            String name = vertex.get("name");
            if (name == null) {
                name = vertex.getType();
            }
            dotfile.addNode("V" + Integer.toHexString(vertex.hashCode()), name, "blue");
        }
        for (Edge edge : schemaGraph.getEdges()) {
            dotfile.addEdge("V" + Integer.toHexString(edge.getFrom().hashCode()), "V" + Integer.toHexString(edge.getTo().hashCode()), edge.getLabel());
        }
        return dotfile.print();
    }
}
