package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLInputObjectType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;


    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<>();

    /**
     * <p>Constructor for GraphQLInputObjectType.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param fields a {@link java.util.List} object.
     */
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

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }

    /**
     * <p>getFields.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLInputObjectField> getFields() {
        return new ArrayList<>(fieldMap.values());
    }

    /**
     * <p>getField.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLInputObjectField} object.
     */
    public GraphQLInputObjectField getField(String name) {
        return fieldMap.get(name);
    }

    /**
     * <p>newInputObject.</p>
     *
     * @return a {@link graphql.schema.GraphQLInputObjectType.Builder} object.
     */
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
