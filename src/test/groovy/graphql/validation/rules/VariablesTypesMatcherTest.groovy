package graphql.validation.rules

import graphql.language.BooleanValue
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
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType) == result

        where:
        variableType            | defaultValue           | expectedType        || result
        GraphQLString           | null                   | GraphQLString       || true
        list(GraphQLString)     | null                   | list(GraphQLString) || true
        nonNull(GraphQLBoolean) | new BooleanValue(true) | GraphQLBoolean      || true
        nonNull(GraphQLString)  | null                   | list(GraphQLString) || false
    }
}
