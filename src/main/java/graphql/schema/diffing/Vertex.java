package graphql.schema.diffing;

import graphql.Assert;
import graphql.Internal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Internal
public class Vertex {

    private String type;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private String debugName;
    private boolean isolated;
    private String graphqlTypeForIsolatedVertex;


    private boolean builtInType;

    private Collection<Edge> adjacentEdges = Collections.emptyList();
    private Collection<Edge> adjacentEdgesInverse = Collections.emptyList();

    public static Vertex newIsolatedNode(String debugName, String graphqlType) {
        Vertex vertex = new Vertex(SchemaGraph.ISOLATED, debugName);
        vertex.isolated = true;
        vertex.graphqlTypeForIsolatedVertex = graphqlType;
        return vertex;
    }

    public static Set<Vertex> newIsolatedNodes(int count, String debugName, String graphqlType) {
        Set<Vertex> result = new LinkedHashSet<>();
        for (int i = 1; i <= count; i++) {
            Vertex vertex = new Vertex(SchemaGraph.ISOLATED, debugName + i);
            vertex.isolated = true;
            vertex.graphqlTypeForIsolatedVertex = graphqlType;
            result.add(vertex);
        }
        return result;
    }

    public Vertex(String type, String debugName) {
        this.type = type;
        this.debugName = debugName;
    }

    public boolean isIsolated() {
        return isolated;
    }

    public String getGraphqlTypeForIsolatedVertex() {
        return graphqlTypeForIsolatedVertex;
    }

    public void add(String propName, Object propValue) {
        properties.put(propName, propValue);
    }

    public String getType() {
        return type;
    }

    public <T> T get(String propName) {
        return (T) properties.get(propName);
    }

    public <T> T getProperty(String name) {
        return (T) properties.get(name);
    }

    public String getName() {
        return (String) Assert.assertNotNull(properties.get("name"), "should not call getName on %s", this);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getDebugName() {
        return debugName;
    }

    public boolean isOfType(String type) {
        return this.type.equals(type);
    }

    public boolean isEqualTo(Vertex other) {
        return other != null &&
                Objects.equals(this.type, other.type) &&
                Objects.equals(this.properties, other.properties);
    }

    public boolean isBuiltInType() {
        return builtInType;
    }

    public void setBuiltInType(boolean builtInType) {
        this.builtInType = builtInType;
    }

    public Collection<Edge> getAdjacentEdges() {
        return adjacentEdges;
    }

    public void setAdjacentEdges(Collection<Edge> adjacentEdges) {
        this.adjacentEdges = adjacentEdges;
    }

    public Collection<Edge> getAdjacentEdgesInverse() {
        return adjacentEdgesInverse;
    }

    public void setAdjacentEdgesInverse(Collection<Edge> adjacentEdgesInverse) {
        this.adjacentEdgesInverse = adjacentEdgesInverse;
    }


    @Override
    public String toString() {
        return "Vertex{" +
                "type='" + type + '\'' +
                ", properties=" + properties.toString().replace("\n", "<NL>") +
                ", debugName='" + debugName + '\'' +
                ", builtInType='" + builtInType + '\'' +
                '}';
    }

    public VertexData toData() {
        return new VertexData(this.type, this.properties);
    }

    public static class VertexData {
        private final String type;
        private final Map<String, Object> properties;

        public VertexData(String type, Map<String, Object> properties) {
            this.type = type;
            this.properties = properties;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VertexData that = (VertexData) o;
            return Objects.equals(type, that.type) && Objects.equals(properties, that.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, properties);
        }
    }

}
