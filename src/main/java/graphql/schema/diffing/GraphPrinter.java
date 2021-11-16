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
            String nameOne = edge.getOne().get("name");
            String nameTwo = edge.getTwo().get("name");
            dotfile.addEdge("V" + Integer.toHexString(edge.getOne().hashCode()), "V" + Integer.toHexString(edge.getTwo().hashCode()), edge.getLabel());
        }
        return dotfile.print();
    }
}
