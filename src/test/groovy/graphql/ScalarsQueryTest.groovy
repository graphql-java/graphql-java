package graphql

import graphql.execution.batched.BatchedExecutionStrategy
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsQueryTest extends Specification {


    def 'Float NaN Not a Number '() {
        given:
        def query = """
        query FloatNaN {
          floatNaN
        }
        """
        def expected = [
                floatNaN: null
        ]

        when:
        def result = GraphQL.newGraphQL(ScalarsQuerySchema.scalarsQuerySchema)
                .build().execute(query)
        def resultBatched = GraphQL.newGraphQL(ScalarsQuerySchema.scalarsQuerySchema)
                .queryExecutionStrategy(new BatchedExecutionStrategy())
                .build().execute(query)

        then:
        thrown(GraphQLException)
    }

    def 'Escaped characters are handled'() {
        given:
        def query = """
        query {
          stringInput(input: "test \\\\ \\" \\/ \\b \\f \\n \\r \\t \\u12Aa")
        }
        """
        def expected = [
                stringInput: "test \\ \" / \b \f \n \r \t \u12Aa",
        ]

        when:
        def result = GraphQL.newGraphQL(ScalarsQuerySchema.scalarsQuerySchema).build().execute(query)

        then:
        result.data == expected
        result.errors.empty == true
    }

    @Unroll
    def "FooBar String cannot be cast to #number"() {
        given:
        def query = "{ " + number + "String(input: \"foobar\") }"

        when:
        def result = GraphQL.newGraphQL(ScalarsQuerySchema.scalarsQuerySchema).build().execute(query)

        then:
        result.errors[0] instanceof SerializationError

        where:
        number       | _
        "float"      | _
        "int"        | _
    }
}
