package graphql.cats.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class Kit {

    @SuppressWarnings("unchecked")
    static List<Map> listOfMaps(Object yamlObj) {
        if (yamlObj instanceof Map) {
            return Collections.singletonList((Map) yamlObj);
        }
        if (yamlObj instanceof List) {
            return (List<Map>) yamlObj;
        }
        return null;
    }
}
