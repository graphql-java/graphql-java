package graphql.schema.diffing;


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import graphql.collect.ImmutableKit;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class SchemaGraph {

    private List<Vertex> vertices = new ArrayList<>();
    private Map<Vertex, List<Edge>> fromEdges = new LinkedHashMap<>();
    private List<Edge> edges = new ArrayList<>();

    private Map<String, Vertex> typesByName = new LinkedHashMap<>();
    private Map<String, Vertex> directivesByName = new LinkedHashMap<>();
    private Table<Vertex, Vertex, Edge> edgeByVertexPair = HashBasedTable.create();

    public void addVertex(Vertex vertex) {
        vertices.add(vertex);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
        fromEdges.computeIfAbsent(edge.getFrom(), ignored -> new ArrayList<>()).add(edge);
        edgeByVertexPair.put(edge.getFrom(), edge.getTo(), edge);
    }

    public List<Edge> getEdges(Vertex from) {
        return fromEdges.getOrDefault(from, Collections.emptyList());
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public @Nullable Edge getEdge(Vertex from, Vertex to) {
        return edgeByVertexPair.get(from, to);
    }

    public List<Vertex> getTargetVertices(Vertex from) {
        return ImmutableKit.map(fromEdges.get(from), Edge::getTo);
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public void addType(String name, Vertex vertex) {
        typesByName.put(name, vertex);
    }

    public void addDirective(String name, Vertex vertex) {
        directivesByName.put(name, vertex);
    }

    public Vertex getType(String name) {
        return typesByName.get(name);
    }

    public Vertex getDirective(String name) {
        return directivesByName.get(name);
    }

    public Optional<Vertex> findTargetVertex(Vertex from, Predicate<Vertex> vertexPredicate) {
        return fromEdges.get(from).stream().map(Edge::getTo).filter(vertexPredicate).findFirst();
    }

    public int size() {
        return vertices.size();
    }
}
