package graphql.util;

import java.util.List;
import java.util.Map;

public interface NodeAdapter<T> {

    Map<String, List<T>> getNamedChildren(T node);

    T withNewChildren(T node, Map<String, List<T>> newChildren);

    T removeChild(T node, NodeLocation location);

}
