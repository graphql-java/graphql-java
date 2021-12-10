package graphql.schema.diffing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Vertex {

    private String type;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private String debugName;
    private boolean artificialNode;


    public static Vertex newArtificialNode(String type) {
        Vertex vertex = new Vertex(type, null);
        vertex.artificialNode = true;
        return vertex;
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

    @Override
    public String toString() {
        return "Vertex{" +
                "type='" + type + '\'' +
                ", properties=" + properties +
                ", debugName='" + debugName + '\'' +
                '}';
    }
}
