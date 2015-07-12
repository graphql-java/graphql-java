package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;


    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<>();

    public GraphQLInputObjectType(String name, List<GraphQLInputObjectField> fields) {
        this.name = name;
        buildMap(fields);
    }

    private void buildMap(List<GraphQLInputObjectField> fields) {
        for (GraphQLInputObjectField field : fields) {
            fieldMap.put(field.getName(), field);
        }
    }

    public String getName() {
        return name;
    }

    public List<GraphQLInputObjectField> getFields() {
        return new ArrayList<>(fieldMap.values());
    }

    public List<GraphQLInputObjectField> getField(String name) {
        return new ArrayList<>(fieldMap.values());
    }
}
