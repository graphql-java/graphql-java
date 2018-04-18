package graphql.schema2;


import graphql.PublicApi;
import graphql.language.FieldDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeReference;

import java.util.List;

/**
 * Fields are the ways you get data values in graphql and a field definition represents a field, its type, the arguments it takes
 * and the {@link DataFetcher} used to get data values for that field.
 *
 * Fields can be thought of as functions in graphql, they have a name, take defined arguments and return a value.
 *
 * Fields can also be deprecated, which indicates the consumers that a field wont be supported in the future.
 *
 * See http://graphql.org/learn/queries/#fields for more details on the concept.
 */
@PublicApi
public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private final GraphQLTypeReference type;
    private final String deprecationReason;
    private final List<GraphQLArgument> arguments;
    private final FieldDefinition definition;

    public GraphQLFieldDefinition(String name, String description, GraphQLTypeReference type, String deprecationReason, List<GraphQLArgument> arguments, FieldDefinition definition) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.deprecationReason = deprecationReason;
        this.arguments = arguments;
        this.definition = definition;
    }

    public String getName() {
        return name;
    }


    public GraphQLOutputType getType() {
        return type;
    }

    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    public List<GraphQLArgument> getArguments() {
        return arguments;
    }

    public String getDescription() {
        return description;
    }

    public FieldDefinition getDefinition() {
        return definition;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    @Override
    public String toString() {
        return "GraphQLFieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", arguments=" + arguments +
                ", description='" + description + '\'' +
                ", deprecationReason='" + deprecationReason + '\'' +
                ", definition=" + definition +
                '}';
    }

}
