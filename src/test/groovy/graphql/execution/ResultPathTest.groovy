package graphql.execution

import graphql.AssertException
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.SerializationError
import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification
import spock.lang.Unroll

class ResultPathTest extends Specification {

    @Unroll
    "unit test toList works as expected : #actual"() {

        expect:
        actual.toList() == expected

        where:
        actual                                                     || expected
        ResultPath.rootPath()                                      || []
        ResultPath.rootPath().segment("A")                         || ["A"]
        ResultPath.rootPath().segment("A").segment(1).segment("B") || ["A", 1, "B"]
        ResultPath.rootPath().segment("A").segment("B").segment(1) || ["A", "B", 1]
    }

    @Unroll
    "unit test toString works as expected : #actual"() {
        expect:
        actual.toString() == expected

        where:
        actual                                                        || expected
        ResultPath.rootPath()                                      || ""
        ResultPath.rootPath().segment("A")                         || "/A"
        ResultPath.rootPath().segment("A").segment(1).segment("B") || "/A[1]/B"
        ResultPath.rootPath().segment("A").segment("B").segment(1) || "/A/B[1]"
    }

    @Unroll
    "unit test sibling works as expected : #actual"() {

        expect:
        actual.toList() == expected

        where:
        actual                                                                     || expected
        ResultPath.rootPath()                                                   || []
        ResultPath.rootPath().segment("A").sibling("B")                         || ["B"]
        ResultPath.rootPath().segment("A").segment(1).segment("B").sibling("C") || ["A", 1, "C"]
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

        def graphQL = TestUtil.graphQL(spec,
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
                ]).build()


        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("""
                    {
                        f1 
                        f2 {
                            sub1 
                            sub2
                        } 
                        aliasedF3 : f3
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
        ["aliasedF3"] == error3.getPath()

        def error4 = errors.get(3) as NonNullableFieldWasNullError
        ["f4", "nonNullField"] == error4.getPath()
    }

    def "test best case parsing"() {


        expect:
        ResultPath.parse(pathString).toList() == expectedList

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
        ResultPath.parse(badPathString)

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

    @Unroll
    def "test from test fromList #inputList and #expectedString"() {


        expect:
        ResultPath.fromList(inputList).toString() == expectedString

        where:

        expectedString | inputList
        ""             | []
        "/a"           | ["a"]
        "/a/b"         | ["a", "b"]
        "/a/b[0]/c[1]" | ["a", "b", 0, "c", 1]
    }

    def "get path without list end"() {
        when:
        def path = ResultPath.fromList(["a", "b", 9])
        path = path.getPathWithoutListEnd()
        then:
        path.toList() == ["a", "b"]

        when:
        path = path.getPathWithoutListEnd()
        then:
        path.toList() == ["a", "b"]
    }


    def "can append paths"() {
        when:
        def path = ResultPath.fromList(["a", "b", 0])
        def path2 = ResultPath.fromList(["x", "y", 9])

        def newPath = path.append(path2)

        then:
        newPath.toList() == ["a", "b", 0, "x", "y", 9]


        when:
        newPath = ResultPath.rootPath().append(path2)

        then:
        newPath.toList() == ["x", "y", 9]

        when:
        newPath = path2.append(ResultPath.rootPath())

        then:
        newPath.toList() == ["x", "y", 9]

        when:
        newPath = ResultPath.rootPath().append(ResultPath.rootPath())

        then:
        newPath.toList() == []
    }

    def "replace support"() {
        when:
        def path = ResultPath.fromList(["a", "b", 0])
        def newPath = path.replaceSegment(1)

        then:
        newPath.toList() == ["a", "b", 1]

        when:
        newPath = path.replaceSegment("x")

        then:
        newPath.toList() == ["a", "b", "x"]

        when:
        newPath = path.replaceSegment(99)

        then:
        newPath.toList() == ["a", "b", 99]

        when:
        ResultPath.rootPath().replaceSegment(1)

        then:
        thrown(AssertException)

        when:
        ResultPath.rootPath().replaceSegment("x")

        then:
        thrown(AssertException)

        when:
        newPath = ResultPath.parse("/a/b[1]").replaceSegment("x")
        then:
        newPath.toList() == ["a", "b", "x"]
    }
}
