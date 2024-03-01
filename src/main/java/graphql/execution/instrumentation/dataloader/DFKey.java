package graphql.execution.instrumentation.dataloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DFKey {

    private final List<String> keys;

    public DFKey(List<String> keys) {
        this.keys = keys;
    }

    public List<String> getKeys() {
        return keys;
    }

    @Override
    public String toString() {
        return "DFKey{" +
                "keys=" + keys +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DFKey dfKey = (DFKey) o;
        return Objects.equals(keys, dfKey.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys);
    }

    public DFKey withAdditionalKey(String key) {
        ArrayList<String> keys = new ArrayList<>(this.keys);
        keys.add(key);
        return new DFKey(keys);
    }
}
