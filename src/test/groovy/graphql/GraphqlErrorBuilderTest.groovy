package graphql

import graphql.execution.ExecutionPath
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
    def stepInfo = newExecutionStepInfo().path(ExecutionPath.fromList(["a", "b"])).type(GraphQLString).build()

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
}