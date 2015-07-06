package graphql.language;


import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectValue implements Value{

    private Map<String,Value> valueMap = new LinkedHashMap<>();

    public Map<String, Value> getValueMap() {
        return valueMap;
    }

    public void setValueMap(Map<String, Value> valueMap) {
        this.valueMap = valueMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectValue that = (ObjectValue) o;

        return !(valueMap != null ? !valueMap.equals(that.valueMap) : that.valueMap != null);

    }

    @Override
    public int hashCode() {
        return valueMap != null ? valueMap.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ObjectValue{" +
                "valueMap=" + valueMap +
                '}';
    }
}
