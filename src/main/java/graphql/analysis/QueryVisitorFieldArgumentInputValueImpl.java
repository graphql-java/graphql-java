package graphql.analysis;

import graphql.Internal;
import graphql.PublicApi;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;

@PublicApi
public class QueryVisitorFieldArgumentInputValueImpl implements QueryVisitorFieldArgumentInputValue {
    private final GraphQLFieldDefinition graphQLFieldDefinition;
    private final GraphQLArgument graphQLArgument;
    private final QueryVisitorFieldArgumentInputValue parent;
    private final String name;
    private final GraphQLInputType inputType;
    private final Value value;

    private QueryVisitorFieldArgumentInputValueImpl(QueryVisitorFieldArgumentInputValue parent, GraphQLFieldDefinition graphQLFieldDefinition, GraphQLArgument graphQLArgument, String name, GraphQLInputType inputType, Value value) {
        this.graphQLFieldDefinition = graphQLFieldDefinition;
        this.graphQLArgument = graphQLArgument;
        this.parent = parent;
        this.name = name;
        this.inputType = inputType;
        this.value = value;
    }

    @Internal
    public static QueryVisitorFieldArgumentInputValue incompleteArgumentInputValue(GraphQLFieldDefinition graphQLFieldDefinition, GraphQLArgument graphQLArgument, String name, GraphQLInputType inputType) {
        return new QueryVisitorFieldArgumentInputValueImpl(null, graphQLFieldDefinition, graphQLArgument,
                name, inputType, null);
    }

    @Internal
    public QueryVisitorFieldArgumentInputValueImpl incompleteNewChild(String name, GraphQLInputType inputType) {
        return new QueryVisitorFieldArgumentInputValueImpl(
                this, this.graphQLFieldDefinition, this.graphQLArgument, name, inputType, null);
    }

    @Internal
    public QueryVisitorFieldArgumentInputValueImpl completeArgumentInputValue(Value<?> value) {
        return new QueryVisitorFieldArgumentInputValueImpl(
                this.parent, this.graphQLFieldDefinition, this.graphQLArgument,
                this.name, this.inputType, value);
    }


    @Override
    public GraphQLFieldDefinition getGraphQLFieldDefinition() {
        return graphQLFieldDefinition;
    }

    @Override
    public GraphQLArgument getGraphQLArgument() {
        return graphQLArgument;
    }

    @Override
    public QueryVisitorFieldArgumentInputValue getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GraphQLInputType getInputType() {
        return inputType;
    }

    @Override
    public Value getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" +
                "parent=" + parent +
                ", name='" + name + '\'' +
                ", inputType=" + inputType +
                ", value=" + value +
                '}';
    }
}
