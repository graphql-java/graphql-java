package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;


    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<>();

    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public List<GraphQLInputObjectField> getFields() {
        return new ArrayList<>(fieldMap.values());
    }

    public GraphQLInputObjectField getField(String name) {
        return fieldMap.get(name);
    }

    public static Builder newInputObject() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLInputObjectField> fields = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder field(GraphQLInputObjectField field) {
            fields.add(field);
            return this;
        }

        public GraphQLInputObjectType build() {
            return new GraphQLInputObjectType(name, description, fields);
        }

    }
}
