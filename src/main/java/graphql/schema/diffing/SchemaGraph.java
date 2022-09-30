package graphql.schema.diffing;


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import graphql.Assert;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static java.lang.String.format;

public class SchemaGraph {

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
    public static final String DUMMY_TYPE_VERTEX = "__DUMMY_TYPE_VERTEX";
    public static final String ISOLATED = "__ISOLATED";

    public static final List<String> ALL_TYPES = Arrays.asList(DUMMY_TYPE_VERTEX, OBJECT, INTERFACE, UNION, INPUT_OBJECT, SCALAR, ENUM, ENUM_VALUE, APPLIED_DIRECTIVE, FIELD, ARGUMENT, APPLIED_ARGUMENT, DIRECTIVE, INPUT_FIELD);
    public static final List<String> ALL_NAMED_TYPES = Arrays.asList(OBJECT, INTERFACE, UNION, INPUT_OBJECT, SCALAR, ENUM);

    /**
     * SCHEMA,
     * SCALAR,
     * OBJECT,
     * FIELD_DEFINITION,
     * ARGUMENT_DEFINITION,
     * INTERFACE,
     * UNION,
     * ENUM,
     * ENUM_VALUE,
     * INPUT_OBJECT,
     * INPUT_FIELD_DEFINITION
     */
    public static final List<String> appliedDirectiveContainerTypes = Arrays.asList(SCALAR, OBJECT, FIELD, ARGUMENT, INTERFACE, UNION, ENUM, ENUM_VALUE, INPUT_OBJECT, INPUT_FIELD);

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

    public void removeVertexAndEdges(Vertex vertex) {
        vertices.remove(vertex);
        typeToVertices.remove(vertex.getType(), vertex);
        for (Iterator<Edge> it = edges.iterator(); it.hasNext(); ) {
            Edge edge = it.next();
            if (edge.getOne().equals(vertex) || edge.getTwo().equals(vertex)) {
                edgeByVertexPair.remove(edge.getOne(), edge.getTwo());
                edgeByVertexPair.remove(edge.getTwo(), edge.getOne());
                it.remove();
            }
        }
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

    public Vertex getAppliedDirectiveForAppliedArgument(Vertex appliedArgument) {
        List<Vertex> adjacentVertices = this.getAdjacentVertices(appliedArgument, vertex -> vertex.getType().equals(APPLIED_DIRECTIVE));
        assertTrue(adjacentVertices.size() == 1, () -> format("No applied directive found for %s", appliedArgument));
        return adjacentVertices.get(0);
    }

    public Vertex getAppliedDirectiveContainerForAppliedDirective(Vertex appliedDirective) {
        List<Vertex> adjacentVertices = this.getAdjacentVertices(appliedDirective, vertex -> !vertex.getType().equals(APPLIED_ARGUMENT));
        assertTrue(adjacentVertices.size() == 1, () -> format("No applied directive container found for %s", appliedDirective));
        return adjacentVertices.get(0);
    }

    public Vertex getParentSchemaElement(Vertex vertex) {
        switch (vertex.getType()) {
            case OBJECT:
                break;
            case INTERFACE:
                break;
            case UNION:
                break;
            case FIELD:
                return getFieldsContainerForField(vertex);
            case ARGUMENT:
                return getFieldOrDirectiveForArgument(vertex);
            case SCALAR:
                break;
            case ENUM:
                break;
            case ENUM_VALUE:
                break;
            case INPUT_OBJECT:
                break;
            case INPUT_FIELD:
                return getInputObjectForInputField(vertex);
            case DIRECTIVE:
                break;
            case APPLIED_DIRECTIVE:
                break;
            case APPLIED_ARGUMENT:
                break;
            case DUMMY_TYPE_VERTEX:
                break;
            case ISOLATED:
                return Assert.assertShouldNeverHappen();
        }
        return assertShouldNeverHappen();
    }

    public Vertex getEnumForEnumValue(Vertex enumValue) {
        List<Vertex> adjacentVertices = this.getAdjacentVertices(enumValue, vertex -> vertex.getType().equals(ENUM));
        assertTrue(adjacentVertices.size() == 1, () -> format("No enum found for %s", enumValue));
        return adjacentVertices.get(0);
    }

    public Vertex getFieldOrInputFieldForDummyType(Vertex enumValue) {
        List<Vertex> adjacentVertices = this.getAdjacentVertices(enumValue, vertex -> vertex.getType().equals(FIELD) || vertex.getType().equals(INPUT_FIELD));
        assertTrue(adjacentVertices.size() == 1, () -> format("No field or input field found for %s", enumValue));
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
