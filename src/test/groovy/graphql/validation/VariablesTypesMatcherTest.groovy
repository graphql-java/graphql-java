package graphql.validation

import graphql.language.BooleanValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.language.TypeName
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
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType, false) == result

        where:
        variableType            | defaultValue           | expectedType        || result
        new TypeName("String")                   | null                   | GraphQLString       || true
        new ListType(new TypeName("String"))     | null                   | list(GraphQLString) || true
        new NonNullType(new TypeName("Boolean")) | new BooleanValue(true) | GraphQLBoolean      || true
        new NonNullType(new TypeName("String"))  | null                   | list(GraphQLString) || false
    }

    @Unroll
    def "issue 3276 - #variableType with default value #defaultValue and expected #expectedType with location default #hasLocationDefault should result: #result "() {

        expect:
        typesMatcher.doesVariableTypesMatch(variableType, defaultValue, expectedType, hasLocationDefault) == result

        where:
        variableType          | defaultValue        | expectedType           | hasLocationDefault || result
        new TypeName("String") | null                | nonNull(GraphQLString) | false              || false
        new TypeName("String") | null                | nonNull(GraphQLString) | true               || true
        new TypeName("String") | StringValue.of("x") | nonNull(GraphQLString) | true               || true
        new TypeName("String") | StringValue.of("x") | nonNull(GraphQLString) | false              || true
    }

    @Unroll
    def "effective variable type for #variableType and default #defaultValue is #effectiveType"() {
        expect:
        typesMatcher.effectiveTypeName(variableType, defaultValue) == effectiveType

        where:
        variableType                            | defaultValue                     || effectiveType
        new TypeName("String")                  | null                             || "String"
        new TypeName("String")                  | NullValue.newNullValue().build() || "String"
        new TypeName("String")                  | StringValue.of("x")              || "String!"
        new NonNullType(new TypeName("String")) | StringValue.of("x")              || "String!"
        new ListType(new TypeName("String"))     | null                             || "[String]"
    }
}
