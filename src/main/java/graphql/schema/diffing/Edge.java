package graphql.schema.diffing;

public class Edge {
    private Vertex from;
    private Vertex to;

    private String label = "";

    public Edge(Vertex from, Vertex to) {
        this.from = from;
        this.to = to;
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
}
