package graphql.schema.diffing;


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import graphql.ExperimentalApi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static graphql.Assert.assertTrue;
import static java.lang.String.format;

@ExperimentalApi
public class SchemaGraph {

    public static final String SCHEMA = "Schema";
    public static final String OBJECT = "Object";
    public static final String INTERFACE = "Interface";
    public static final String UNION = "Union";
    public static final String FIELD = "Field";
    public static final String ARGUMENT = "Argument";
    public static final String SCALAR = "Scalar";
    public static final String ENUM = "Enum";
    public static final String ENUM_VALUE = "EnumValue";
    public static final String INPUT_OBJECT = "InputObject";
    public static final String INPUT_FIELD = "InputField";
    public static final String DIRECTIVE = "Directive";
    public static final String APPLIED_DIRECTIVE = "AppliedDirective";
    public static final String APPLIED_ARGUMENT = "AppliedArgument";
    public static final String ISOLATED = "__ISOLATED";


    private List<Vertex> vertices = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    private Map<String, Vertex> typesByName = new LinkedHashMap<>();
    private Map<String, Vertex> directivesByName = new LinkedHashMap<>();
    private Table<Vertex, Vertex, Edge> edgesByDirection = HashBasedTable.create();
    private Table<Vertex, Vertex, Edge> edgesByInverseDirection = HashBasedTable.create();
    private Multimap<String, Vertex> typeToVertices = LinkedHashMultimap.create();

    public SchemaGraph() {

    }

    public SchemaGraph(List<Vertex> vertices, List<Edge> edges, Table<Vertex, Vertex, Edge> edgeByVertexPair) {
        this.vertices = vertices;
        this.edges = edges;
        this.edgesByDirection = edgeByVertexPair;
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

    public Multimap<String, Vertex> getVerticesByType() {
        return typeToVertices;
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
        edgesByDirection.put(edge.getFrom(), edge.getTo(), edge);
        edgesByInverseDirection.put(edge.getTo(), edge.getFrom(), edge);
    }

    //
//    public List<Edge> getAdjacentEdges(Vertex from) {
//        return new ArrayList<>(edgesByDirection.row(from).values());
//    }
    public Collection<Edge> getAdjacentEdgesNonCopy(Vertex from) {
        return edgesByDirection.row(from).values();
    }

    public Iterable<Edge> getAdjacentEdgesAndInverseNonCopy(Vertex fromAndTo) {
        Collection<Edge> edges = edgesByInverseDirection.row(fromAndTo).values();
        Collection<Edge> edgesInverse = edgesByDirection.row(fromAndTo).values();
        return Iterables.concat(edges, edgesInverse);
    }

    public int adjacentEdgesAndInverseCount(Vertex fromAndTo) {
        return edgesByInverseDirection.row(fromAndTo).size() + edgesByDirection.row(fromAndTo).size();
    }

    public List<Vertex> getAdjacentVertices(Vertex from) {
        return getAdjacentVertices(from, x -> true);
    }

    public List<Vertex> getAdjacentVertices(Vertex from, Predicate<Vertex> predicate) {
        List<Vertex> result = new ArrayList<>();
        for (Edge edge : edgesByDirection.row(from).values()) {
            Vertex v = edge.getTo();
            if (predicate.test(v)) {
                result.add(v);
            }
        }
        return result;
    }

    public List<Vertex> getAdjacentVerticesInverse(Vertex to) {
        return getAdjacentVerticesInverse(to, x -> true);
    }

    public List<Vertex> getAdjacentVerticesInverse(Vertex to, Predicate<Vertex> predicate) {
        List<Vertex> result = new ArrayList<>();
        for (Edge edge : edgesByInverseDirection.row(to).values()) {
            Vertex v = edge.getFrom();
            if (predicate.test(v)) {
                result.add(v);
            }
        }
        return result;
    }

    public List<Edge> getAdjacentEdges(Vertex from, Predicate<Vertex> predicate) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edgesByDirection.row(from).values()) {
            Vertex v = edge.getTo();
            if (predicate.test(v)) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<Edge> getAdjacentEdgesInverseCopied(Vertex to) {
        return new ArrayList<>(edgesByInverseDirection.row(to).values());
    }

    public Collection<Edge> getAdjacentEdgesInverseNonCopy(Vertex to) {
        return edgesByInverseDirection.row(to).values();
    }

    public List<Edge> getAdjacentEdgesInverse(Vertex to, Predicate<Vertex> predicate) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edgesByInverseDirection.row(to).values()) {
            Vertex v = edge.getFrom();
            if (predicate.test(v)) {
                result.add(edge);
            }
        }
        return result;
    }

    public Edge getSingleAdjacentEdge(Vertex from, Predicate<Edge> predicate) {
        for (Edge edge : edgesByDirection.row(from).values()) {
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
        return edgesByDirection.get(from, to);
    }

    public @Nullable Edge getEdgeOrInverse(Vertex from, Vertex to) {
        Edge result = edgesByDirection.get(from, to);
        return result != null ? result : edgesByInverseDirection.get(from, to);
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
        return edgesByDirection.row(from).values().stream().map(Edge::getTo).filter(vertexPredicate).findFirst();
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
        List<Vertex> adjacentVertices = getAdjacentVerticesInverse(argument);
        assertTrue(adjacentVertices.size() == 1, () -> format("No field or directive found for %s", argument));
        return adjacentVertices.get(0);
    }

    public Vertex getFieldsContainerForField(Vertex field) {
        List<Vertex> adjacentVertices = getAdjacentVerticesInverse(field);
        assertTrue(adjacentVertices.size() == 1, () -> format("No fields container found for %s", field));
        return adjacentVertices.get(0);
    }

    public Vertex getInputObjectForInputField(Vertex inputField) {
        List<Vertex> adjacentVertices = this.getAdjacentVerticesInverse(inputField);
        assertTrue(adjacentVertices.size() == 1, () -> format("No input object found for %s", inputField));
        return adjacentVertices.get(0);
    }

    public Vertex getAppliedDirectiveForAppliedArgument(Vertex appliedArgument) {
        List<Vertex> adjacentVertices = this.getAdjacentVerticesInverse(appliedArgument);
        assertTrue(adjacentVertices.size() == 1, () -> format("No applied directive found for %s", appliedArgument));
        return adjacentVertices.get(0);
    }

    public Vertex getAppliedDirectiveContainerForAppliedDirective(Vertex appliedDirective) {
        List<Vertex> adjacentVertices = this.getAdjacentVerticesInverse(appliedDirective);
        assertTrue(adjacentVertices.size() == 1, () -> format("No applied directive container found for %s", appliedDirective));
        return adjacentVertices.get(0);
    }

    /**
     * Gets the one inverse adjacent edge to the input and gets the other vertex.
     *
     * @param input  the vertex input
     * @return a vertex
     */
    public Vertex getSingleAdjacentInverseVertex(Vertex input) {
        Collection<Edge> adjacentVertices = this.getAdjacentEdgesInverseNonCopy(input);
        assertTrue(adjacentVertices.size() == 1, () -> format("No parent found for %s", input));
        return adjacentVertices.iterator().next().getFrom();
    }

    public int getAppliedDirectiveIndex(Vertex appliedDirective) {
        List<Edge> adjacentEdges = this.getAdjacentEdgesInverseCopied(appliedDirective);
        assertTrue(adjacentEdges.size() == 1, () -> format("No applied directive container found for %s", appliedDirective));
        return Integer.parseInt(adjacentEdges.get(0).getLabel());
    }

    public Vertex getEnumForEnumValue(Vertex enumValue) {
        List<Vertex> adjacentVertices = this.getAdjacentVerticesInverse(enumValue);
        assertTrue(adjacentVertices.size() == 1, () -> format("No enum found for %s", enumValue));
        return adjacentVertices.get(0);
    }


    public List<Edge> getAllAdjacentEdges(List<Vertex> fromList, Vertex to) {
        List<Edge> result = new ArrayList<>();
        for (Vertex from : fromList) {
            Edge edge = getEdge(from, to);
            if (edge == null) {
                continue;
            }
            result.add(edge);
        }
        return result;
    }

}
