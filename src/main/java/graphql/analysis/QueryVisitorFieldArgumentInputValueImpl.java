package graphql.analysis;

import graphql.Internal;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;

@Internal
public class QueryVisitorFieldArgumentInputValueImpl implements QueryVisitorFieldArgumentInputValue {
    private final GraphQLInputValueDefinition inputValueDefinition;
    private final Value value;
    private final QueryVisitorFieldArgumentInputValue parent;

    private QueryVisitorFieldArgumentInputValueImpl(QueryVisitorFieldArgumentInputValue parent, GraphQLInputValueDefinition inputValueDefinition, Value value) {
        this.parent = parent;
        this.inputValueDefinition = inputValueDefinition;
        this.value = value;
    }

    static QueryVisitorFieldArgumentInputValue incompleteArgumentInputValue(GraphQLArgument graphQLArgument) {
        return new QueryVisitorFieldArgumentInputValueImpl(
                null, graphQLArgument, null);
    }

    QueryVisitorFieldArgumentInputValueImpl incompleteNewChild(GraphQLInputObjectField inputObjectField) {
        return new QueryVisitorFieldArgumentInputValueImpl(
                this, inputObjectField, null);
    }

    QueryVisitorFieldArgumentInputValueImpl completeArgumentInputValue(Value<?> value) {
        return new QueryVisitorFieldArgumentInputValueImpl(
                this.parent, this.inputValueDefinition, value);
    }


    @Override
    public QueryVisitorFieldArgumentInputValue getParent() {
        return parent;
    }

    public GraphQLInputValueDefinition getInputValueDefinition() {
        return inputValueDefinition;
    }

    @Override
    public String getName() {
        return inputValueDefinition.getName();
    }

    @Override
    public GraphQLInputType getInputType() {
        return inputValueDefinition.getType();
    }

    @Override
    public Value getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "QueryVisitorFieldArgumentInputValueImpl{" +
                "inputValue=" + inputValueDefinition +
                ", value=" + value +
                '}';
    }
}
