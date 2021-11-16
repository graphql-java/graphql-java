package graphql.schema.diffing.dot;

import java.util.ArrayList;
import java.util.List;


public class Dotfile {

    public static class Node {
        public Node(String id, String label, String color) {
            this.id = id;
            this.label = label;
            this.color = color;
        }

        String id;
        String label;
        String color;
    }

    public static class Edge {
        public Edge(String from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }

        String from;
        String to;
        String label;
    }

    public static class SubGraph {

        String id;
        String label;
        List<Edge> edges = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();

        public SubGraph(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public void addEdge(Edge e) {
            edges.add(e);
        }

        public void addNode(Node node) {
            nodes.add(node);
        }

    }


    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private List<SubGraph> subGraphs = new ArrayList<>();


    public void addNode(Node node) {
        nodes.add(node);
    }

    public void addNode(String id, String label, String color) {
        nodes.add(new Node(id, label, color));
    }

    public void addEdge(String from, String to, String label) {
        edges.add(new Edge(from, to, label));
    }

    public void addEdge(Edge e) {
        edges.add(e);
    }

    public void addSubgraph(SubGraph subGraph) {
        subGraphs.add(subGraph);
    }

    public String getId() {
        return "";
    }

    public String print() {
        StringBuilder result = new StringBuilder();
        result.append("graph G {\n");
        for (Node node : nodes) {
            result.append(node.id).append("[label=\"").append(node.label).append("\" color=").append(node.color).append(" style=filled").append("];\n");
        }
        for (Edge edge : edges) {
            result.append(edge.from).append(" -- ").append(edge.to).append("[label=\"").append(edge.label).append("\"];\n");
        }
        for (SubGraph subGraph : subGraphs) {
            result.append("subgraph cluster_").append(subGraph.id).append("{\n").append("label=\"").append(subGraph.label).append("\";\n");
            for (Node node : subGraph.nodes) {
                result.append(node.id).append("[label=\"").append(node.label).append("\" color=").append(node.color).append(" style=filled").append("];\n");
            }
            for (Edge edge : subGraph.edges) {
                result.append(edge.from).append(" -- ").append(edge.to).append("[label=\"").append(edge.label).append("\"];\n");
            }
            result.append("}");

        }
//        result.append(explanation());
        result.append("}");
        return result.toString();
    }

    String explanation() {
        return "subgraph cluster_explanation {\n" +
                "label=\"Explanation\";\n" +
                "concept [color=green style=filled label=\"Concept\"];\n" +
                "orgEntity [color=lightblue style=filled label=\"Org Entity\"];\n" +
                "product [color=red style=filled label=\"Product\"];\n" +
                "concept -> product [style=invis];\n" +
                "orgEntity -> concept [style=invis];\n" +
                "}";
    }
}
