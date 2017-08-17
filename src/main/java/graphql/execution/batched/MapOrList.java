package graphql.execution.batched;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapOrList {


    private Map<String, Object> mapResults;
    private List<Object> listResults;

    public static MapOrList createListResult(List<Object> results) {
        MapOrList mapOrList = new MapOrList();
        mapOrList.listResults = results;
        return mapOrList;
    }

    public static MapOrList createMapResult(Map<String, Object> result) {
        MapOrList mapOrList = new MapOrList();
        mapOrList.mapResults = result;
        return mapOrList;
    }

    public MapOrList createMapResultForField(String fieldName) {
        Map<String, Object> map = new LinkedHashMap<>();
        putResult(fieldName, map);
        return createMapResult(map);
    }

    public MapOrList createListResultForField(String fieldName) {
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


    public Object getResult() {
        if (this.mapResults != null) {
            return this.mapResults;
        } else {
            return this.listResults;
        }
    }
}
