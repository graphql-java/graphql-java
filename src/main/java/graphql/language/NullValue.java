package graphql.language;


import java.util.Collections;
import java.util.List;

public class NullValue extends AbstractNode implements Value {

    public static NullValue Null = new NullValue();

    private NullValue() {
    }

    @Override
    public List<Node> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }

    @Override
    public String toString() {
        return "NullValue{" +
                '}';
    }
}
