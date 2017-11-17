package graphql.execution

import graphql.*
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStrategyParameters.newParameters
import static graphql.execution.ExecutionTypeInfo.newTypeInfo
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class AbsoluteGraphQLErrorTest extends Specification {

    def fieldDefinition = newFieldDefinition()
            .name("someField")
            .type(GraphQLString)
            .build()
    def objectType = newObject()
            .name("Test")
            .field(fieldDefinition)
            .build()

    def "constructor works as expected"() {
        given:

        def field = new Field("test")
        field.setSourceLocation(new SourceLocation(4, 5))

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [new Field()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah", ["fld"])

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getMessage() == "blah"
        error.getPath() == ["foo", "bar", "fld"]
        error.getLocations().size() == 1
        error.getLocations().get(0).getColumn() == 15
        error.getLocations().get(0).getLine() == 6
        error.getErrorType() == relativeError.getErrorType()
    }

    def "constructor handles missing path as null"() {
        given:

        def field = new Field("test")
        field.setSourceLocation(new SourceLocation(4, 5))

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [new Field()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getPath() == null
    }

    def "constructor handles missing locations as null"() {
        given:

        def field = new Field("test")

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [new Field()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getLocations() == null
    }

    def "constructor transforms multiple source locations"() {
        given:

        def field = new Field("test")
        field.setSourceLocation(new SourceLocation(4, 5))

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [new Field()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah", ["fld"])
        relativeError.locations = [new SourceLocation(1, 5), new SourceLocation(3, 6)]

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getLocations() == [new SourceLocation(5, 10), new SourceLocation(7, 11)]
    }
}
