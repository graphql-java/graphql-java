package graphql.execution

import graphql.AssertException
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLError
import graphql.SerializationError
import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionPathTest extends Specification {

    @Unroll
    "unit test toList works as expected : #actual"() {

        expect:
        actual.toList() == expected

        where:
        actual                                                        || expected
        ExecutionPath.rootPath()                                      || []
        ExecutionPath.rootPath().segment("A")                         || ["A"]
        ExecutionPath.rootPath().segment("A").segment(1).segment("B") || ["A", 1, "B"]
        ExecutionPath.rootPath().segment("A").segment("B").segment(1) || ["A", "B", 1]
    }

    @Unroll
    "unit test toString works as expected : #actual"() {
        expect:
        actual.toString() == expected

        where:
        actual                                                        || expected
        ExecutionPath.rootPath()                                      || ""
        ExecutionPath.rootPath().segment("A")                         || "/A"
        ExecutionPath.rootPath().segment("A").segment(1).segment("B") || "/A[1]/B"
        ExecutionPath.rootPath().segment("A").segment("B").segment(1) || "/A/B[1]"
    }


    def "full integration test of path support"() {
        when:

        def spec = """
        type Query {
            f1 : String
            f2 : [Test] 
            f3 : Float
            f4 : NonNullType
        }
        
        type Test {
            sub1 : String
            sub2 : String
        }
        
        type NonNullType {
            nonNullField : String!
        }
            
        """


        def f1Fetcher = { env -> throw new RuntimeException("error") } as DataFetcher
        def f2Fetcher = { env -> [false, true, false] } as DataFetcher
        def f3Fetcher = { env -> "This is not a float" } as DataFetcher
        def sub1Fetcher = { env -> "staticValue" } as DataFetcher
        def sub2Fetcher = { env ->
            boolean willThrow = env.getSource()
            if (willThrow) {
                throw new RuntimeException("error")
            }
            return "no error"
        } as DataFetcher

        def f4Fetcher = { env -> "Some Value" } as DataFetcher
        def nonNullFieldFetcher = { env -> null } as DataFetcher

        def schema = TestUtil.schema(spec,
                ["Query"      :
                         [
                                 "f1": f1Fetcher,
                                 "f2": f2Fetcher,
                                 "f3": f3Fetcher,
                                 "f4": f4Fetcher
                         ],
                 "Test"       :
                         [
                                 "sub1": sub1Fetcher,
                                 "sub2": sub2Fetcher
                         ],
                 "NonNullType":
                         [
                                 "nonNullField": nonNullFieldFetcher
                         ]
                ])


        GraphQL graphQL = GraphQL.newGraphQL(schema).build()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("""
                    {
                        f1 
                        f2 {
                            sub1 
                            sub2
                        } 
                        f3
                        f4 {
                          nonNullField
                        }
                    }
                        """)
                .build()

        List<GraphQLError> errors = graphQL.execute(executionInput).getErrors()

        then:

        errors.size() == 4

        def error = errors.get(0) as ExceptionWhileDataFetching
        ["f1"] == error.getPath()

        def error2 = errors.get(1) as ExceptionWhileDataFetching
        ["f2", 1, "sub2"] == error2.getPath()

        def error3 = errors.get(2) as SerializationError
        ["f3"] == error3.getPath()

        def error4 = errors.get(3) as NonNullableFieldWasNullError
        ["f4", "nonNullField"] == error4.getPath()
    }

    def "test best case parsing"() {


        expect:
        ExecutionPath.parse(pathString).toList() == expectedList

        where:

        pathString     | expectedList
        ""             | []
        null           | []
        "/a"           | ["a"]
        "/a/b"         | ["a", "b"]
        " /a/b "       | ["a", "b"]
        "/a/b[0]/c[1]" | ["a", "b", 0, "c", 1]
    }

    def "test worst case parsing"() {

        when:
        ExecutionPath.parse(badPathString)

        then:
        thrown(AssertException)

        where:

        badPathString | _
        "a"           | _
        "a/b"         | _
        "a/b[x]"      | _
        "a/b[0"       | _
        "a/b[0/c[1]"  | _
        "a/b[0]c[1/"  | _
        "/"           | _
    }

    def "test from test fromList"() {


        expect:
        ExecutionPath.fromList(inputList).toString() == expectedString

        where:

        expectedString | inputList
        ""             | []
        "/a"           | ["a"]
        "/a/b"         | ["a", "b"]
        "/a/b[0]/c[1]" | ["a", "b", 0, "c", 1]
    }

}
