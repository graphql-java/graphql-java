package graphql.schema.diffing;


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.SchemaGraphFactory.APPLIED_ARGUMENT;
import static graphql.schema.diffing.SchemaGraphFactory.DIRECTIVE;
import static graphql.schema.diffing.SchemaGraphFactory.FIELD;
import static graphql.schema.diffing.SchemaGraphFactory.INPUT_OBJECT;
import static graphql.schema.diffing.SchemaGraphFactory.INTERFACE;
import static graphql.schema.diffing.SchemaGraphFactory.OBJECT;
import static java.lang.String.format;

public class SchemaGraph {

    private List<Vertex> vertices = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    private Map<String, Vertex> typesByName = new LinkedHashMap<>();
    private Map<String, Vertex> directivesByName = new LinkedHashMap<>();
    private Table<Vertex, Vertex, Edge> edgeByVertexPair = HashBasedTable.create();
    private Multimap<String, Vertex> typeToVertices = LinkedHashMultimap.create();

    public SchemaGraph() {

    }

    public SchemaGraph(List<Vertex> vertices, List<Edge> edges, Table<Vertex, Vertex, Edge> edgeByVertexPair) {
        this.vertices = vertices;
        this.edges = edges;
        this.edgeByVertexPair = edgeByVertexPair;
    }

    public void addVertex(Vertex vertex) {
        vertices.add(vertex);
        typeToVertices.put(vertex.getType(), vertex);
    }

    public void addVertices(Collection<Vertex> vertices) {
        for (Vertex vertex : vertices) {
            this.vertices.add(vertex);
            typeToVertices.put(vertex.getType(), vertex);
        }
    }

    public Collection<Vertex> getVerticesByType(String type) {
        return typeToVertices.get(type);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
        edgeByVertexPair.put(edge.getOne(), edge.getTwo(), edge);
        edgeByVertexPair.put(edge.getTwo(), edge.getOne(), edge);
    }

    public List<Edge> getAdjacentEdges(Vertex from) {
        return new ArrayList<>(edgeByVertexPair.row(from).values());
    }

    public List<Vertex> getAdjacentVertices(Vertex from) {
        return getAdjacentVertices(from, x -> true);
    }

    public List<Vertex> getAdjacentVertices(Vertex from, Predicate<Vertex> predicate) {
        List<Vertex> result = new ArrayList<>();
        for (Edge edge : edgeByVertexPair.row(from).values()) {
            Vertex v;
            if (edge.getOne() == from) {
                v = edge.getTwo();
            } else {
                v = edge.getOne();
            }
            if (predicate.test(v)) {
                result.add(v);
            }
        }
        return result;
    }

    public Edge getSingleAdjacentEdge(Vertex from, Predicate<Edge> predicate) {
        for (Edge edge : edgeByVertexPair.row(from).values()) {
            if (predicate.test(edge)) {
                return edge;
            }
        }
        return null;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    // null if the edge doesn't exist
    public @Nullable Edge getEdge(Vertex from, Vertex to) {
        return edgeByVertexPair.get(from, to);
    }


    public List<Vertex> getVertices() {
        return vertices;
    }

    public void setVertices(List<Vertex> vertices) {
        this.vertices = vertices;
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
        return edgeByVertexPair.row(from).values().stream().map(Edge::getTwo).filter(vertexPredicate).findFirst();
    }

    public int size() {
        return vertices.size();
    }

    public List<Vertex> addIsolatedVertices(int count, String debugPrefix) {
        List<Vertex> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vertex isolatedVertex = Vertex.newIsolatedNode(debugPrefix + i);
            vertices.add(isolatedVertex);
            result.add(isolatedVertex);
        }
        return result;
    }

    public Vertex getFieldOrDirectiveForArgument(Vertex argument) {
        List<Vertex> adjacentVertices = getAdjacentVertices(argument, vertex -> vertex.getType().equals(FIELD) || vertex.getType().equals(DIRECTIVE));
        assertTrue(adjacentVertices.size() == 1, () -> format("No field or directive found for %s", argument));
        return adjacentVertices.get(0);
    }

    public Vertex getFieldsContainerForField(Vertex field) {
        List<Vertex> adjacentVertices = getAdjacentVertices(field, vertex -> vertex.getType().equals(OBJECT) || vertex.getType().equals(INTERFACE));
        assertTrue(adjacentVertices.size() == 1, () -> format("No fields container found for %s", field));
        return adjacentVertices.get(0);
    }

    public Vertex getInputObjectForInputField(Vertex inputField) {
        List<Vertex> adjacentVertices = this.getAdjacentVertices(inputField, vertex -> vertex.getType().equals(INPUT_OBJECT));
        assertTrue(adjacentVertices.size() == 1, () -> format("No input object found for %s", inputField));
        return adjacentVertices.get(0);
    }


}
