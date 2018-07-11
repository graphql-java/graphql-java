package graphql.execution

import graphql.DataFetchingErrorGraphQLError
import graphql.language.Field
import graphql.language.SourceLocation
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStrategyParameters.newParameters
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

        def field = Field.newField().name("test").sourceLocation(new SourceLocation(4, 5)).build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah", ["fld"]) {
            @Override
            Map<String, Object> getExtensions() {
                return ["ext": true]
            }
        }

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getMessage() == "blah"
        error.getPath() == ["foo", "bar", "fld"]
        error.getLocations().size() == 1
        error.getLocations().get(0).getColumn() == 15
        error.getLocations().get(0).getLine() == 6
        error.getErrorType() == relativeError.getErrorType()
        error.getExtensions() == ["ext": true]
    }

    def "constructor handles missing path as null"() {
        given:

        def field = Field.newField().name("test").sourceLocation(new SourceLocation(4, 5)).build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getPath() == null
    }

    def "when constructor receives empty path it should return the base field path"() {
        given:

        def field = Field.newField().name("test").sourceLocation(new SourceLocation(4, 5)).build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")
        relativeError.path = []

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getPath() == ["foo", "bar"]
    }

    def "constructor handles missing locations as null"() {
        given:

        def field = Field.newField().name("test").build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")

        when:

        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getLocations() == null
    }

    def "when constructor receives empty locations it should return the base field locations"() {
        given:

        def expectedSourceLocation = new SourceLocation(1, 2)
        def field = Field.newField().name("test").sourceLocation(expectedSourceLocation).build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
                .field([field])
                .path(ExecutionPath.fromList(["foo", "bar"]))
                .build()

        def relativeError = new DataFetchingErrorGraphQLError("blah")
        relativeError.locations = []

        when:
        def error = new AbsoluteGraphQLError(parameters, relativeError)

        then:

        error.getLocations() == [expectedSourceLocation]
    }

    def "constructor transforms multiple source locations"() {
        given:

        def field = Field.newField().name("test").sourceLocation(new SourceLocation(4, 5)).build()

        def parameters = newParameters()
                .typeInfo(ExecutionTypeInfo.newTypeInfo().type(objectType))
                .source(new Object())
                .fields(["fld": [Field.newField().build()]])
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
