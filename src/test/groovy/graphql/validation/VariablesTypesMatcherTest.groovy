package graphql.validation

import graphql.language.BooleanValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class VariablesTypesMatcherTest extends Specification {

    VariablesTypesMatcher typesMatcher = new VariablesTypesMatcher()


    @Unroll
    def "#variableType with default value #defaultValue and expected #expectedType should result: #result "() {

        expect:
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType, null) == result

        where:
        variableType            | defaultValue           | expectedType        || result
        GraphQLString           | null                   | GraphQLString       || true
        list(GraphQLString)     | null                   | list(GraphQLString) || true
        nonNull(GraphQLBoolean) | new BooleanValue(true) | GraphQLBoolean      || true
        nonNull(GraphQLString)  | null                   | list(GraphQLString) || false
    }

    @Unroll
    def "issue 3276 - #variableType with default value #defaultValue and expected #expectedType with #locationDefaultValue should result: #result "() {

        expect:
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType, locationDefaultValue) == result

        where:
        variableType  | defaultValue        | expectedType           | locationDefaultValue || result
        GraphQLString | null                | nonNull(GraphQLString) | null                 || false
        GraphQLString | null                | nonNull(GraphQLString) | StringValue.of("x")  || true
        GraphQLString | StringValue.of("x") | nonNull(GraphQLString) | StringValue.of("x")  || true
        GraphQLString | StringValue.of("x") | nonNull(GraphQLString) | null                 || true
    }
}
