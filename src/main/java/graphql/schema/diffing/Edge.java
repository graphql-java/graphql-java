package graphql.schema.diffing;

import java.util.LinkedHashMap;
import java.util.Map;

public class Edge {
    private Vertex from;
    private Vertex to;

    private Map<String, Object> properties = new LinkedHashMap<>();

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


    public void add(String propName, Object propValue) {
        properties.put(propName, propValue);
    }

    public <T> T get(String propName) {
        return (T) properties.get(propName);
    }


}
