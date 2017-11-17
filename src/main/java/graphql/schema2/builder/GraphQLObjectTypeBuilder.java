package graphql.schema2.builder;

import graphql.language.ObjectTypeDefinition;
import graphql.schema2.GraphQLFieldDefinition;
import graphql.schema2.GraphQLOutputType;

import java.util.ArrayList;
import java.util.List;

public class GraphQLObjectTypeBuilder {

    private String name;
    private String description;
    private final List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();
    private final List<GraphQLOutputType> interfaces = new ArrayList<>();
    private ObjectTypeDefinition definition;

    public GraphQLObjectTypeBuilder name(String name) {
        this.name = name;
        return this;
    }

    public GraphQLObjectTypeBuilder description(String description) {
        this.description = description;
        return this;
    }

    public GraphQLObjectTypeBuilder definition(ObjectTypeDefinition definition) {
        this.definition = definition;
        return this;
    }

    public GraphQLObjectTypeBuilder field(GraphQLFieldDefinition fieldDefinition) {
        assertNotNull(fieldDefinition, "fieldDefinition can't be null");
        this.fieldDefinitions.add(fieldDefinition);
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
     *
     * @return this
     */
    public GraphQLObjectTypeBuilder field(UnaryOperator<GraphQLFieldDefinition.Builder> builderFunction) {
        assertNotNull(builderFunction, "builderFunction can't be null");
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder = builderFunction.apply(builder);
        return field(builder.build());
    }

    /**
     * Same effect as the field(GraphQLFieldDefinition). Builder.build() is called
     * from within
     *
     * @param builder an un-built/incomplete GraphQLFieldDefinition
     *
     * @return this
     */
    public GraphQLObjectTypeBuilder field(GraphQLFieldDefinition.Builder builder) {
        this.fieldDefinitions.add(builder.build());
        return this;
    }

    public Builder fields(List<GraphQLFieldDefinition> fieldDefinitions) {
        assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
        this.fieldDefinitions.addAll(fieldDefinitions);
        return this;
    }

    public Builder withInterface(GraphQLInterfaceType interfaceType) {
        assertNotNull(interfaceType, "interfaceType can't be null");
        this.interfaces.add(interfaceType);
        return this;
    }

    public Builder withInterface(GraphQLTypeReference reference) {
        assertNotNull(reference, "reference can't be null");
        this.interfaces.add(reference);
        return this;
    }

    public Builder withInterfaces(GraphQLInterfaceType... interfaceType) {
        for (GraphQLInterfaceType type : interfaceType) {
            withInterface(type);
        }
        return this;
    }

    public Builder withInterfaces(GraphQLTypeReference... references) {
        for (GraphQLTypeReference reference : references) {
            withInterface(reference);
        }
        return this;
    }

    public GraphQLObjectType build() {
        return new GraphQLObjectType(name, description, fieldDefinitions, interfaces, definition);
    }

}
}
