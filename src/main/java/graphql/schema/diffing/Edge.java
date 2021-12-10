package graphql.schema.diffing;

import java.util.Objects;

public class Edge {
    private Vertex one;
    private Vertex two;

    private String label = "";

    public Edge(Vertex from, Vertex to) {
        this.one = from;
        this.two = to;
    }

    public Edge(Vertex from, Vertex to, String label) {
        this.one = from;
        this.two = to;
        this.label = label;
    }

    public Vertex getOne() {
        return one;
    }

    public void setOne(Vertex one) {
        this.one = one;
    }

    public Vertex getTwo() {
        return two;
    }

    public void setTwo(Vertex two) {
        this.two = two;
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
                "one=" + one +
                ", two=" + two +
                ", label='" + label + '\'' +
                '}';
    }

    public boolean isEqualTo(Edge other) {
        return Objects.equals(this.label, other.label);
    }
}
