package graphql.validation.rules

import graphql.language.BooleanValue
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString


class VariablesTypesMatcherTest extends Specification {

    VariablesTypesMatcher typesMatcher = new VariablesTypesMatcher()


    @Unroll
    def "#variableType with default value #defaultValue and expected #expectedType should result: #result "() {

        expect:
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType) == result

        where:
        variableType                       | defaultValue           | expectedType                   || result
        GraphQLString                      | null                   | GraphQLString                  || true
        new GraphQLList(GraphQLString)     | null                   | new GraphQLList(GraphQLString) || true
        new GraphQLNonNull(GraphQLBoolean) | new BooleanValue(true) | GraphQLBoolean                 || true
        new GraphQLNonNull(GraphQLString)  | null                   | new GraphQLList(GraphQLString) || false
    }
}
