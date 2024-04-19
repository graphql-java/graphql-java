package graphql.schema.diffing;


import java.util.Objects;

public class SingleMapping {

    private final Vertex from;
    private final Vertex to;

    public SingleMapping(Vertex from, Vertex to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleMapping that = (SingleMapping) o;
        return Objects.equals(from, that.from) && Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    public Vertex getFrom() {
        return from;
    }

    public Vertex getTo() {
        return to;
    }

    @Override
    public String toString() {
        return "SingleMapping{" +
                "from=" + from +
                ", to=" + to +
                '}';
    }

    public String toDebugNames() {
        return "SingleMapping{" +
                "from=" + from.getDebugName() +
                ", to=" + to.getDebugName() +
                '}';
    }
}
