package graphql.schema.diffing;

import java.util.LinkedHashMap;
import java.util.Map;

public class Vertex {

    private String type;
    private Map<String, Object> properties = new LinkedHashMap<>();

    public Vertex(String type) {
        this.type = type;
    }

    public void add(String propName, Object propValue) {
        properties.put(propName,propValue);
    }

    public String getType() {
        return type;
    }
    public <T> T get(String propName) {
       return (T) properties.get(propName);
    }
}
