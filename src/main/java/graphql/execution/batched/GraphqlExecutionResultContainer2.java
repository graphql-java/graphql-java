package graphql.execution.batched;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GraphqlExecutionResultContainer2 {

    /**
     * Creates a child datum which is linked through the results container to this parent.
     */
    public GraphqlExecutionNodeDatum22 createAndPutChildDatum(String fieldName, Object value) {
        Map<String,Object> map = new HashMap<>();
        putResult(fieldName, map);
        return new GraphqlExecutionNodeDatum22(map, value);
    }

    public GraphqlExecutionResultList2 createAndPutEmptyChildList(String fieldName) {
        List<Object> resultList = new ArrayList<>();
        putResult(fieldName, resultList);
        return new GraphqlExecutionResultList2(resultList);
    }

    /**
     * Inserts this result into the parent for the specified field.
     */
    abstract void putResult(String fieldName, Object value);

}
