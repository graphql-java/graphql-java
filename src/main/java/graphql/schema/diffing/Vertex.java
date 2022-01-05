package graphql.schema.diffing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Vertex {

    private String type;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private String debugName;
    private boolean artificialNode;


    private boolean builtInType;

    public static Vertex newArtificialNode(String debugName) {
        Vertex vertex = new Vertex(SchemaGraphFactory.ISOLATED, debugName);
        vertex.artificialNode = true;
        return vertex;
    }

    public static Set<Vertex> newArtificialNodes(int count, String debugName) {
        Set<Vertex> result = new LinkedHashSet<>();
        for (int i = 1; i <= count; i++) {
            Vertex vertex = new Vertex(SchemaGraphFactory.ISOLATED, debugName + i);
            vertex.artificialNode = true;
            result.add(vertex);
        }
        return result;
    }

    public Vertex(String type, String debugName) {
        this.type = type;
        this.debugName = debugName;
    }

    public boolean isArtificialNode() {
        return artificialNode;
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

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getDebugName() {
        return debugName;
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

    @Override
    public String toString() {
        return "Vertex{" +
                "type='" + type + '\'' +
                ", properties=" + properties +
                ", debugName='" + debugName + '\'' +
                ", builtInType='" + builtInType + '\'' +
                '}';
    }

}
