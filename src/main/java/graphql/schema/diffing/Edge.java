package graphql.schema.diffing;

import graphql.ExperimentalApi;

import java.util.Objects;

@ExperimentalApi
public class Edge {
    private Vertex from;
    private Vertex to;

    private String label = "";

    public Edge(Vertex from, Vertex to) {
        this.from = from;
        this.to = to;
    }

    public Edge(Vertex from, Vertex to, String label) {
        this.from = from;
        this.to = to;
        this.label = label;
    }

    public Vertex getFrom() {
        return from;
    }

    public void setFrom(Vertex from) {
        this.from = from;
    }

    public Vertex getTo() {
        return to;
    }

    public void setTo(Vertex to) {
        this.to = to;
    }


    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "from=" + from +
                ", to=" + to +
                ", label='" + label + '\'' +
                '}';
    }

    public boolean isEqualTo(Edge other) {
        return Objects.equals(this.label, other.label);
    }
}
