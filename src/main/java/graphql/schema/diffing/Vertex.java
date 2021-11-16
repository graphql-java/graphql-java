package graphql.schema.diffing;

import java.util.LinkedHashMap;
import java.util.Map;

public class Vertex {

    private String type;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private String debugName;

    public Vertex(String type) {
        this.type = type;
    }
    public Vertex(String type, String debugName) {
        this.type = type;
        this.debugName = debugName;
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

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getDebugName() {
        return debugName;
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
