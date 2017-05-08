package graphql.schema;

import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputObjectTypeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

@PublicApi
public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLInputFieldsContainer {

    private final String name;
    private final String description;
    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<>();
    private final InputObjectTypeDefinition definition;

    @Internal
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
        this(name,description,fields,null);
    }

    @Internal
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields, InputObjectTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(fields, "fields can't be null");
        this.name = name;
        this.description = description;
        this.definition = definition;
        buildMap(fields);
    }

    private void buildMap(List<GraphQLInputObjectField> fields) {
        for (GraphQLInputObjectField field : fields) {
            String name = field.getName();
            if (fieldMap.containsKey(name))
                throw new AssertException("field " + name + " redefined");
            fieldMap.put(name, field);
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


    @Override
    public GraphQLInputObjectField getFieldDefinition(String name) {
        return fieldMap.get(name);
    }

    @Override
    public List<GraphQLInputObjectField> getFieldDefinitions() {
        return new ArrayList<>(fieldMap.values());
    }

    public InputObjectTypeDefinition getDefinition() {
        return definition;
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private InputObjectTypeDefinition definition;
        private List<GraphQLInputObjectField> fields = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder definition(InputObjectTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder field(GraphQLInputObjectField field) {
            assertNotNull(field, "field can't be null");
            fields.add(field);
            return this;
        }

        /**
         * Take a field builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      field(f -> f.name("fieldName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         * @return this
         */
        public Builder field(UnaryOperator<GraphQLInputObjectField.Builder> builderFunction) {
            assertNotNull(builderFunction, "builderFunction should not be null");
            GraphQLInputObjectField.Builder builder = GraphQLInputObjectField.newInputObjectField();
            builder = builderFunction.apply(builder);
            return field(builder);
        }

        /**
         * Same effect as the field(GraphQLFieldDefinition). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLFieldDefinition
         * @return this
         */
        public Builder field(GraphQLInputObjectField.Builder builder) {
            this.fields.add(builder.build());
            return this;
        }

        public Builder fields(List<GraphQLInputObjectField> fields) {
            for (GraphQLInputObjectField field : fields) {
                field(field);
            }
            return this;
        }

        public GraphQLInputObjectType build() {
            return new GraphQLInputObjectType(name, description, fields, definition);
        }

    }
}
