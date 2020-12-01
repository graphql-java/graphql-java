package graphql

import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.relay.DefaultConnectionCursor
import graphql.schema.CoercingParseLiteralException
import graphql.validation.Validator
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsIDTest extends Specification {

    @Unroll
    def "ID parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == result

        where:
        literal                         | result
        new StringValue("1234ab")       | "1234ab"
        new IntValue(123 as BigInteger) | "123"

    }

    @Unroll
    def "ID returns null for invalid #literal"() {
        when:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
    }

    @Unroll
    def "ID serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLID.getCoercing().serialize(value) == result
        Scalars.GraphQLID.getCoercing().parseValue(value) == result

        where:
        value                                                   | result
        "123ab"                                                 | "123ab"
        123                                                     | "123"
        123123123123123123L                                     | "123123123123123123"
        new BigInteger("123123123123123123")                    | "123123123123123123"
        UUID.fromString("037ebc7a-f9b8-4d76-89f6-31b34a40e10b") | "037ebc7a-f9b8-4d76-89f6-31b34a40e10b"
        new DefaultConnectionCursor("cursor123")                | "cursor123"
    }

    @Unroll
    def "serialize allows any object via String.valueOf #value"() {
        when:
        Scalars.GraphQLID.getCoercing().serialize(value)
        then:
        noExceptionThrown()

        where:
        value        | _
        new Object() | _

    }

    @Unroll
    def "parseValue allows any object via String.valueOf #value"() {
        when:
        Scalars.GraphQLID.getCoercing().parseValue(value)
        then:
        noExceptionThrown()

        where:
        value        | _
        new Object() | _

    }

    def "parseLiteral: ensure ID is not Boolean "() {
        def spec = '''
            type Query {
               example(id: ID!): Example
            }
            type Example {
               id: ID!
               name: String!
            }
        '''

        def schema = TestUtil.schema(spec)

        def dsl = '''
            query{
               example(id: true) {
                  id
                 name
               }
            }
        '''

        when:
        def document = TestUtil.parseQuery(dsl)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document) as List

        then:
        validationErrors.size() == 1
        validationErrors.get(0).getMessage() == 'Validation error of type WrongType: argument \'id\' with value \'BooleanValue{value=true}\' is not a valid \'ID\' - Expected AST type \'IntValue\' or \'StringValue\' but was \'BooleanValue\'. @ \'example\''
    }


    def "parseValue: ensure ID is not Boolean "() {
        def spec = '''
            type Query {
               example(id: ID!): Example
            }
            type Example {
               id: ID!
               name: String!
            }
        '''
        def schema = TestUtil.schema(spec)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def dsl = '''
            query($id: ID!){
              example(id: $id) {
                id
                name
              }
            }
        '''
        def variable = ["id": true]
        def input = ExecutionInput.newExecutionInput().query(dsl).variables(variable).build()

        when:
        def result = graphQL.execute(input)

        then:
        result.errors.size() == 1
        result.errors.get(0).message == 'Variable \'id\' has an invalid value : Expected type \'ID\' but was \'Boolean\'.'
    }

}
