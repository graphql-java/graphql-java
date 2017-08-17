package graphql.execution.batched;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultContainer {

    private Object source;

    private Map<String, Object> mapResults;
    private List<Object> listResults;

    public static ResultContainer createListResult(List<Object> results) {
        ResultContainer resultContainer = new ResultContainer();
        resultContainer.listResults = results;
        return resultContainer;
    }

    public static ResultContainer createMapResult(Map<String, Object> result, Object source) {
        ResultContainer resultContainer = new ResultContainer();
        resultContainer.source = source;
        resultContainer.mapResults = result;
        return resultContainer;
    }

    public ResultContainer createAndPutChildDatum(String fieldName, Object source) {
        Map<String, Object> map = new LinkedHashMap<>();
        putResult(fieldName, map);
        return createMapResult(map, source);
    }

    public ResultContainer createAndPutEmptyChildList(String fieldName) {
        List<Object> resultList = new ArrayList<>();
        putResult(fieldName, resultList);
        return createListResult(resultList);
    }

    public void putResult(String fieldName, Object value) {
        if (this.mapResults != null) {
            this.mapResults.put(fieldName, value);
        } else {
            this.listResults.add(value);
        }
    }

    public Object getSource() {
        return source;
    }

    public Object getResult() {
        if (this.mapResults != null) {
            return this.mapResults;
        } else {
            return this.listResults;
        }
    }
}
