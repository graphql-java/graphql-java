package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;


    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<String, GraphQLInputObjectField>();

    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
        assertNotNull(name, "name can't be null");
        assertNotNull(fields, "fields can't be null");
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
        return new ArrayList<GraphQLInputObjectField>(fieldMap.values());
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
        private List<GraphQLInputObjectField> fields = new ArrayList<GraphQLInputObjectField>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder field(GraphQLInputObjectField field) {
            assertNotNull(field, "field can't be null");
            fields.add(field);
            return this;
        }

        public Builder fields(List<GraphQLInputObjectField> fields) {
            for (GraphQLInputObjectField field : fields) {
                field(field);
            }
            return this;
        }

        public GraphQLInputObjectType build() {
            return new GraphQLInputObjectType(name, description, fields);
        }

    }
}
