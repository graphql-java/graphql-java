package graphql

import graphql.execution.ResultPath
import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.MergedField.newMergedField
import static graphql.language.Field.newField

class GraphqlErrorBuilderTest extends Specification {
    def location = new SourceLocation(6, 9)
    def field = newMergedField(newField("f").sourceLocation(location).build()).build()
    def stepInfo = newExecutionStepInfo().path(ResultPath.fromList(["a", "b"])).type(GraphQLString).build()

    def "dfe is passed on"() {
        DataFetchingEnvironment dfe = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(field)
                .executionStepInfo(stepInfo)
                .build()
        when:
        def graphQLError = GraphqlErrorBuilder.newError(dfe).message("Gunfight at the %s corral", "NotOK").build()
        then:
        graphQLError.getMessage() == "Gunfight at the NotOK corral"
        graphQLError.getLocations() == [location]
        graphQLError.getPath() == ["a", "b"]
        graphQLError.getExtensions() == null
    }

    def "basic building"() {
        when:
        def graphQLError = GraphqlErrorBuilder.newError().message("Gunfight at the %s corral", "NotOK").build()
        then:
        graphQLError.getMessage() == "Gunfight at the NotOK corral"
        graphQLError.getErrorType() == ErrorType.DataFetchingException
    }

    def "builder getters work"() {
        when:
        def errorBuilder = GraphqlErrorBuilder.newError()
                .message("Gunfight at the %s corral", "NotOK")
                .location(location)
                .path(["a","b"])
        then:
        errorBuilder.getMessage() == "Gunfight at the NotOK corral"
        errorBuilder.getErrorType() == ErrorType.DataFetchingException
        errorBuilder.getPath() == ["a","b"]
        errorBuilder.getLocations() == [location]
    }

    def "data fetcher result building works"() {
        when:
        def result = GraphqlErrorBuilder.newError().message("Gunfight at the %s corral", "NotOK").toResult()
        then:
        result.getErrors().size() == 1
        result.getData() == null

        def graphQLError = result.getErrors()[0]

        graphQLError.getMessage() == "Gunfight at the NotOK corral"
        graphQLError.getErrorType() == ErrorType.DataFetchingException
    }

    def "integration test of this"() {
        def sdl = '''
            type Query {
                field(arg : String) : String
            }    
        '''
        DataFetcher df = { env ->
            GraphqlErrorBuilder.newError(env).message("Things are having %s", env.getArgument("arg")).toResult()
        }
        def graphQL = TestUtil.graphQL(sdl, [Query: [field: df]]).build()

        when:
        def er = graphQL.execute({ input -> input.query('{ field(arg : "problems") }') })
        then:
        er.getData() == [field: null]
        er.getErrors().size() == 1
        def graphQLError = er.getErrors()[0]

        graphQLError.getMessage() == "Things are having problems"
        graphQLError.getExtensions() == null
        graphQLError.getErrorType() == ErrorType.DataFetchingException
        graphQLError.getPath() == ["field"]
        graphQLError.getLocations() == [new SourceLocation(1, 3)]
    }

    def "java string format is safe"() {
        when:
        def gqlErr = GraphqlErrorBuilder.newError().message("This has %s in it").build()
        then:
        gqlErr.getMessage() == "This has %s in it"

        when:
        gqlErr = GraphqlErrorBuilder.newError().message("This has %s in it", null).build()
        then:
        gqlErr.getMessage() == "This has %s in it"

        when:
        gqlErr = GraphqlErrorBuilder.newError().message("This has %s in it", new Object[0]).build()
        then:
        gqlErr.getMessage() == "This has %s in it"

        when:
        gqlErr = GraphqlErrorBuilder.newError().message("This has %s in it", "data").build()
        then:
        gqlErr.getMessage() == "This has data in it"
    }

    def "null message is not acceptable"() {
        when:
        GraphqlErrorBuilder.newError().message(null, "a", "b").build()
        then:
        thrown(AssertException)

        when:
        GraphqlErrorBuilder.newError().message(null).build()
        then:
        thrown(AssertException)
    }

    def "can have nullable attributes"() {
        when:
        def error = GraphqlErrorBuilder.newError().message("msg")
                .locations(null)
                .extensions(null)
                .path(null)
                .build()
        then:
        error.message == "msg"
        error.locations == null
        error.path == null
        error.extensions == null
    }

    def "can use a builder direct from graphql error"() {
        when:
        def error = GraphQLError.newError().message("msg")
                .locations(null)
                .extensions([x : "y"])
                .path(null)
                .build()
        then:
        error.message == "msg"
        error.locations == null
        error.extensions == [x : "y"]
        error.path == null

    }

    def "implements equals/hashCode correctly for matching errors"() {
        when:
        def firstError = toGraphQLError(first)
        def secondError = toGraphQLError(second)

        then:
        firstError == secondError
        firstError.hashCode() == secondError.hashCode()

        where:
        first                                                          | second
        [message: "msg"]                                               | [message: "msg"]
        [message: "msg", locations: [new SourceLocation(1, 2)]]        | [message: "msg", locations: [new SourceLocation(1, 2)]]
        [message: "msg", errorType: ErrorType.InvalidSyntax]           | [message: "msg", errorType: ErrorType.InvalidSyntax]
        [message: "msg", path: ["items", 1, "item"]]                   | [message: "msg", path: ["items", 1, "item"]]
        [message: "msg", extensions: [aBoolean: true, aString: "foo"]] | [message: "msg", extensions: [aBoolean: true, aString: "foo"]]
    }

    def "implements equals/hashCode correctly for different errors"() {
        when:
        def firstError = toGraphQLError(first)
        def secondError = toGraphQLError(second)

        then:
        firstError != secondError
        firstError.hashCode() != secondError.hashCode()

        where:
        first                                                   | second
        [message: "msg"]                                        | [message: "different msg"]
        [message: "msg", locations: [new SourceLocation(1, 2)]] | [message: "msg", locations: [new SourceLocation(3, 4)]]
        [message: "msg", errorType: ErrorType.InvalidSyntax]    | [message: "msg", errorType: ErrorType.DataFetchingException]
        [message: "msg", path: ["items", "1", "item"]]          | [message: "msg", path: ["items"]]
        [message: "msg", extensions: [aBoolean: false]]         | [message: "msg", extensions: [aString: "foo"]]
    }

    private static GraphQLError toGraphQLError(Map<String, Object> errorFields) {
        def errorBuilder = GraphQLError.newError();
        errorFields.forEach { key, value ->
            if (value != null) {
                switch (key) {
                    case "message":
                        errorBuilder.message(value as String);
                        break;
                    case "locations":
                        errorBuilder.locations(value as List<SourceLocation>);
                        break;
                    case "errorType":
                        errorBuilder.errorType(value as ErrorClassification);
                        break;
                    case "path":
                        errorBuilder.path(value as List<Object>);
                        break;
                    case "extensions":
                        errorBuilder.extensions(value as Map<String, Object>);
                        break;
                }
            }
        }
        return errorBuilder.build();
    }
}
