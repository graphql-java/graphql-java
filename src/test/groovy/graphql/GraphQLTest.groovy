package graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionId
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.ResultNodesInfo
import graphql.execution.SubscriptionExecutionStrategy
import graphql.execution.ValueUnboxer
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider
import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.LightDataFetcher
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.validation.InvalidSchemaException
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import java.util.function.UnaryOperator

import static graphql.ExecutionInput.Builder
import static graphql.ExecutionInput.newExecutionInput
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.execution.ResultNodesInfo.MAX_RESULT_NODES
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class GraphQLTest extends Specification {

    static GraphQLSchema simpleSchema() {
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
        FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates("RootQueryType", "hello")
        DataFetcher<?> dataFetcher = { env -> "world" }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fieldCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build())
                .build()
        schema
    }

    def "simple query"() {
        given:
        GraphQLSchema schema = simpleSchema()

        when:
        def result = GraphQL.newGraphQL(schema).build().execute('{ hello }').data

        then:
        result == [hello: 'world']
    }

    def "query with sub-fields"() {
        given:
        GraphQLObjectType heroType = newObject()
                .name("heroType")
                .field(
                        newFieldDefinition()
                                .name("id")
                                .type(GraphQLString))
                .field(
                        newFieldDefinition()
                                .name("name")
                                .type(GraphQLString))
                .build()

        GraphQLFieldDefinition.Builder simpsonField = newFieldDefinition()
                .name("simpson")
                .type(heroType)

        FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates("RootQueryType", "simpson")
        DataFetcher<?> dataFetcher = { env -> [id: '123', name: 'homer'] }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fieldCoordinates, dataFetcher)
                .build()

        GraphQLSchema graphQLSchema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name("RootQueryType")
                        .field(simpsonField)
                        .build())
                .build()

        when:
        def result = GraphQL.newGraphQL(graphQLSchema).build().execute('{ simpson { id, name } }').data

        then:
        result == [simpson: [id: '123', name: 'homer']]
    }

    def "query with validation errors"() {
        given:
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .argument(newArgument().name("arg").type(GraphQLString))

        FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates("RootQueryType", "hello")
        DataFetcher<?> dataFetcher = { env -> "hello" }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fieldCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build())
                .build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello(arg:11) }').errors

        then:
        errors.size() == 1
    }

    def "query with invalid syntax"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello(() }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.InvalidSyntax
        errors[0].locations == [new SourceLocation(1, 9)]
    }

    def "query with invalid syntax 2"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello[](() }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.InvalidSyntax
        errors[0].locations == [new SourceLocation(1, 8)]
    }

    def "query with invalid Unicode surrogate in argument - no trailing value"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
                        .build()
        ).build()

        when:
        // Invalid Unicode character - leading surrogate value without trailing surrogate value
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello(arg:"\\ud83c") }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.InvalidSyntax
        errors[0].message == "Invalid unicode encountered. Leading surrogate must be followed by a trailing surrogate. Offending token '\\ud83c' at line 1 column 13"
        errors[0].locations == [new SourceLocation(1, 13)]
    }

    def "non null argument is missing"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ field }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.ValidationError
        errors[0].locations == [new SourceLocation(1, 3)]
        (errors[0] as ValidationError).validationErrorType == ValidationErrorType.MissingFieldArgument
    }

    def "`Iterable` can be used as a `GraphQLList` field result"() {
        given:
        def set = new HashSet<String>()
        set.add("One")
        set.add("Two")

        def queryType = "QueryType"
        def fieldName = "set"
        def fieldCoordinates = FieldCoordinates.coordinates(queryType, fieldName)
        DataFetcher<?> dataFetcher = { set }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fieldCoordinates, dataFetcher)
                .build()

        def schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fieldName)
                                .type(list(GraphQLString)))
                ).build()

        when:
        def data = GraphQL.newGraphQL(schema).build().execute("query { set }").data

        then:
        data == [set: ['One', 'Two']]
    }

    def "document with two operations executes specified operation"() {
        given:

        def queryType = "RootQueryType"
        def field1Name = "field1"
        def field2Name = "field2"
        def field1Coordinates = FieldCoordinates.coordinates(queryType, field1Name)
        def field2Coordinates = FieldCoordinates.coordinates(queryType, field2Name)
        DataFetcher<?> field1DataFetcher = new StaticDataFetcher("value1")
        DataFetcher<?> field2DataFetcher = new StaticDataFetcher("value2")
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(field1Coordinates, field1DataFetcher)
                .dataFetcher(field2Coordinates, field2DataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(field1Name)
                                .type(GraphQLString))
                        .field(newFieldDefinition()
                                .name(field2Name)
                                .type(GraphQLString))
                ).build()

        def query = """
        query Query1 { field1 }
        query Query2 { field2 }
        """

        def expected = [field2: 'value2']

        when:
        def executionInput = newExecutionInput().query(query).operationName('Query2').variables([:])
        def result = GraphQL.newGraphQL(schema).build().execute(executionInput)

        then:
        result.data == expected
        result.errors.size() == 0
    }

    def "document with two operations but no specified operation does not throw"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition().name("name").type(GraphQLString))
        )
                .build()

        def query = """
        query Query1 { name }
        query Query2 { name }
        """

        when:
        def er = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        noExceptionThrown()
        !er.errors.isEmpty()
        er.errors[0].message.contains("Must provide operation name if query contains multiple operations")
    }

    def "null mutation type does not throw an npe but returns and error"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
                        .name("Query")
        )
                .build()

        when:
        def result = GraphQL.newGraphQL(schema).build().execute("mutation { doesNotExist }")

        then:
        result.errors.size() == 1
        ((ValidationError) result.errors[0]).validationErrorType == ValidationErrorType.UnknownOperation
    }

    def "#875 a subscription query against a schema that doesn't support subscriptions should result in a GraphQL error"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("Query")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(GraphQLString)
                                .argument(newArgument()
                                        .name("arg")
                                        .type(GraphQLNonNull.nonNull(GraphQLString))))
        )
                .build()

        when:
        def result = GraphQL.newGraphQL(schema).build().execute("subscription { doesNotExist }")

        then:
        result.errors.size() == 1
        ((ValidationError) result.errors[0]).validationErrorType == ValidationErrorType.UnknownOperation
    }

    def "query with int literal too large"() {
        given:
        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        DataFetcher<?> dataFetcher = { env -> env.getArgument("bar") }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build()))
                ).build()
        def query = "{foo(bar: 12345678910)}"

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 1
        result.errors[0].message == "Validation error (WrongType@[foo]) : argument 'bar' with value 'IntValue{value=12345678910}' is not a valid 'Int' - Expected value to be in the integer range, but it was a '12345678910'"
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with missing argument results in arguments map missing the key"() {
        given:
        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build()))
                ).build()
        def query = "{foo}"

        when:
        GraphQL.newGraphQL(schema).build().execute(query)

        then:
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert !env.arguments.containsKey('bar')
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with null argument results in arguments map with value null "() {
        given:
        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build()))
                ).build()
        def query = "{foo(bar: null)}"

        when:
        GraphQL.newGraphQL(schema).build().execute(query)

        then:
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.containsKey('bar')
            assert env.arguments['bar'] == null
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with missing key in an input object result in a map with missing key"() {
        given:
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("someKey").type(GraphQLString).build())
                .field(newInputObjectField().name("otherKey").type(GraphQLString).build()).build()

        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build()))
                ).build()
        def query = "{foo(bar: {someKey: \"value\"})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.size() == 1
            assert env.arguments["bar"] instanceof Map
            assert env.arguments['bar']['someKey'] == 'value'
            assert !(env.arguments['bar'] as Map).containsKey('otherKey')
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with null value in an input object result in a map with null as value"() {
        given:
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("someKey").type(GraphQLString).build())
                .field(newInputObjectField().name("otherKey").type(GraphQLString).build()).build()

        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build()))
                ).build()
        def query = "{foo(bar: {someKey: \"value\", otherKey: null})}"

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.size() == 1
            assert env.arguments["bar"] instanceof Map
            assert env.arguments['bar']['someKey'] == 'value'
            assert (env.arguments['bar'] as Map).containsKey('otherKey')
            assert env.arguments['bar']['otherKey'] == null
        }
    }

    def "query with missing List input field results in a map with a missing key"() {
        given:
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()

        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build()))
                ).build()
        def query = "{foo(bar: {})}"

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.size() == 1
            assert env.arguments["bar"] instanceof Map
            assert !(env.arguments['bar'] as Map).containsKey('list')
        }
    }

    def "query with null List input field results in a map with null as key"() {
        given:
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()

        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build()))
                ).build()
        def query = "{foo(bar: {list: null})}"

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.size() == 1
            assert env.arguments["bar"] instanceof Map
            assert (env.arguments['bar'] as Map).containsKey('list')
            assert env.arguments['bar']['list'] == null
        }
    }

    def "query with List containing null input field results in a map with a list containing null"() {
        given:
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()

        def queryType = "QueryType"
        def fooName = "foo"
        def fooCoordinates = FieldCoordinates.coordinates(queryType, fooName)
        def dataFetcher = Mock(LightDataFetcher)
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()
        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(newFieldDefinition()
                                .name(fooName)
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build()))
                ).build()
        def query = "{foo(bar: {list: [null]})}"

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_, _, _) >> {
            def env = (it[2] as Supplier<DataFetchingEnvironment>).get()
            assert env.arguments.size() == 1
            assert env.arguments["bar"] instanceof Map
            assert (env.arguments['bar'] as Map).containsKey('list')
            assert env.arguments['bar']['list'] == [null]
        }
    }

    def "#448 invalid trailing braces are handled correctly"() {
        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute("{hero { name }} }")

        then:
        !result.errors.isEmpty()
        result.errors[0].errorType == ErrorType.InvalidSyntax
    }


    def "wrong argument type: array of enum instead of enum"() {
        given:
        GraphQLEnumType enumType = GraphQLEnumType.newEnum().name("EnumType").value("Val1").value("Val2").build()

        GraphQLObjectType queryType = newObject()
                .name("QueryType")
                .field(newFieldDefinition()
                        .name("query")
                        .argument(newArgument().name("fooParam").type(enumType))
                        .type(GraphQLInt))
                .build()

        GraphQLSchema schema = newSchema()
                .query(queryType)
                .build()
        when:
        final GraphQL graphQL = GraphQL.newGraphQL(schema).build()
        final ExecutionResult result = graphQL.execute("{query (fooParam: [Val1,Val2])}")
        then:
        result.errors.size() == 1
        result.errors[0].errorType == ErrorType.ValidationError

    }

    def "execution input passing builder"() {
        given:
        GraphQLSchema schema = simpleSchema()

        when:
        def builder = newExecutionInput().query('{ hello }')
        def result = GraphQL.newGraphQL(schema).build().execute(builder).data

        then:
        result == [hello: 'world']
    }

    def "execution input using builder function"() {
        given:
        GraphQLSchema schema = simpleSchema()

        when:
        def builderFunction = { it.query('{hello}') } as UnaryOperator<Builder>
        def result = GraphQL.newGraphQL(schema).build().execute(builderFunction).data

        then:
        result == [hello: 'world']
    }

    def "execution input passing builder to async"() {
        given:
        GraphQLSchema schema = simpleSchema()

        when:
        def builder = newExecutionInput().query('{ hello }')
        def result = GraphQL.newGraphQL(schema).build().executeAsync(builder).join().data

        then:
        result == [hello: 'world']
    }

    def "execution input using builder function to async"() {
        given:
        GraphQLSchema schema = simpleSchema()

        when:

        def builderFunction = { it.query('{hello}') } as UnaryOperator<Builder>
        def result = GraphQL.newGraphQL(schema).build().executeAsync(builderFunction).join().data

        then:
        result == [hello: 'world']
    }

    @Unroll
    def "abort execution if query depth is too high (#query)"() {
        given:
        def foo = newObject()
                .name("Foo")
                .field(newFieldDefinition()
                        .name("field")
                        .type(typeRef('Foo'))
                        .build())
                .field(newFieldDefinition()
                        .name("scalar")
                        .type(GraphQLString)
                        .build())
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(foo)
                                .build()).build())
                .build()

        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(3)


        def graphql = GraphQL.newGraphQL(schema).instrumentation(maximumQueryDepthInstrumentation).build()

        when:
        def result = graphql.execute(query)

        then:
        result.errors.size() == 1
        result.errors[0].message.contains("maximum query depth exceeded")

        where:
        query                                                                       | _
        "{ field {field {field {field {scalar}}}} }"                                | _
        "{ field {field {field {scalar}}}} "                                        | _
        "{ field {field {field {field {scalar}}}} }"                                | _
        "{ field {field {field {field {field { scalar}}}} }}"                       | _
        "{ f2:field {field {field {scalar}}} f1: field{scalar} f3: field {scalar}}" | _
    }

    @Unroll
    def "abort execution if complexity is too high (#query)"() {
        given:
        def foo = newObject()
                .name("Foo")
                .field(newFieldDefinition()
                        .name("field")
                        .type(typeRef('Foo'))
                        .build())
                .field(newFieldDefinition()
                        .name("scalar")
                        .type(GraphQLString)
                        .build())
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(foo)
                                .build()).build())
                .build()

        MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(3)


        def graphql = GraphQL.newGraphQL(schema).instrumentation(maxQueryComplexityInstrumentation).build()

        when:
        def result = graphql.execute(query)

        then:
        result.errors.size() == 1
        result.errors[0].message.contains("maximum query complexity exceeded")

        where:
        query                                                       | _
        "{ field {field {field {field {scalar}}}} }"                | _
        "{ field {field {field {scalar}}}} "                        | _
        "{ field {field {field {field {scalar}}}} }"                | _
        "{ f2:field {scalar} f1: field{scalar} f3: field {scalar}}" | _
    }

    @Unroll
    def "validation error with (#instrumentationName)"() {
        given:
        GraphQLObjectType foo = newObject()
                .name("Foo")
                .withInterface(typeRef("Node"))
                .field(
                        { field ->
                            field
                                    .name("id")
                                    .type(Scalars.GraphQLID)
                        } as UnaryOperator)
                .build()

        GraphQLInterfaceType node = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(
                        { field ->
                            field
                                    .name("id")
                                    .type(Scalars.GraphQLID)
                        } as UnaryOperator)
                .build()

        GraphQLObjectType query = newObject()
                .name("RootQuery")
                .field(
                        { field ->
                            field
                                    .name("a")
                                    .type(node)
                        } as UnaryOperator)
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(node, { type -> foo })
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(query)
                .build()

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()

        ExecutionInput executionInput = newExecutionInput()
                .query("{a}")
                .build()

        when:
        def result = graphQL.execute(executionInput)

        then:
        result.errors.size() == 1
        result.errors[0].message.contains("Subselection required")

        where:
        instrumentationName    | instrumentation
        'max query depth'      | new MaxQueryDepthInstrumentation(10)
        'max query complexity' | new MaxQueryComplexityInstrumentation(10)
    }

    class CaptureStrategy extends AsyncExecutionStrategy {
        ExecutionId executionId = null
        Instrumentation instrumentation = null

        @Override
        CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
            executionId = executionContext.executionId
            instrumentation = executionContext.instrumentation
            return super.execute(executionContext, parameters)
        }
    }

    def "graphql copying works as expected"() {

        def instrumentation = new SimplePerformantInstrumentation()
        def hello = ExecutionId.from("hello")
        def executionIdProvider = new ExecutionIdProvider() {
            @Override
            ExecutionId provide(String q, String operationName, Object context) {
                return hello
            }
        }

        def queryStrategy = new CaptureStrategy()
        GraphQL graphQL = GraphQL.newGraphQL(simpleSchema())
                .queryExecutionStrategy(queryStrategy)
                .instrumentation(instrumentation)
                .executionIdProvider(executionIdProvider)
                .build()

        when:
        // now copy it as is
        def newGraphQL = graphQL.transform({ builder -> })
        def result = newGraphQL.execute('{ hello }').data

        then:
        result == [hello: 'world']
        queryStrategy.executionId == hello
        queryStrategy.instrumentation instanceof Instrumentation
        queryStrategy.instrumentation == instrumentation

        when:

        // now make some changes
        def newInstrumentation = new SimplePerformantInstrumentation()
        def goodbye = ExecutionId.from("goodbye")
        def newExecutionIdProvider = new ExecutionIdProvider() {
            @Override
            ExecutionId provide(String q, String operationName, Object context) {
                return goodbye
            }
        }

        newGraphQL = graphQL.transform({ builder ->
            builder.executionIdProvider(newExecutionIdProvider).instrumentation(newInstrumentation)
        })
        result = newGraphQL.execute('{ hello }').data

        then:
        result == [hello: 'world']
        queryStrategy.executionId == goodbye
        queryStrategy.instrumentation instanceof SimplePerformantInstrumentation
        newGraphQL.instrumentation == newInstrumentation
    }

    def "provided instrumentation is unchanged"() {
        given:
        def queryStrategy = new CaptureStrategy()
        def instrumentation = new SimplePerformantInstrumentation()
        def builder = GraphQL.newGraphQL(simpleSchema())
                .queryExecutionStrategy(queryStrategy)
                .instrumentation(instrumentation)

        when:
        def graphql = builder
                .build()
        graphql.execute('{ hello }')

        then:
        queryStrategy.instrumentation == instrumentation
    }

    def "query with triple quoted multi line strings"() {
        given:
        def queryType = "Query"
        def fieldName = "hello"
        def fieldCoordinates = FieldCoordinates.coordinates(queryType, fieldName)
        DataFetcher<?> dataFetcher = { env -> env.getArgument("arg") }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fieldCoordinates, dataFetcher)
                .build()

        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name(fieldName)
                .type(GraphQLString)
                .argument(newArgument().name("arg").type(GraphQLString))

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryType)
                        .field(fieldDefinition)
                        .build())
                .build()

        when:
        def result = GraphQL.newGraphQL(schema).build().execute('''{ hello(arg:"""
world
over
many lines""") }''')

        then:
        result.data == [hello: '''world
over
many lines''']
    }

    def "executionId is set before being passed to instrumentation"() {
        InstrumentationCreateStateParameters seenParams

        def instrumentation = new Instrumentation() {

            @Override
            CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters params) {
                seenParams = params
                null
            }
        }

        when:
        GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                .build()
                .execute("{ __typename }")

        then:
        seenParams.executionInput.executionId != null
    }

    def "variables map can't be null via ExecutionInput"() {
        given:

        when:
        def input = newExecutionInput().query('query($var:String){ hello(arg: $var) }').variables(null).build()

        then:
        def assEx = thrown(AssertException)
        assEx.message.contains("variables map can't be null")
    }

    def "query can't be null via ExecutionInput"() {
        given:

        when:
        def input = newExecutionInput().query(null).build()

        then:
        def assEx = thrown(AssertException)
        assEx.message.contains("query can't be null")
    }

    def "query must be set via ExecutionInput"() {
        given:

        when:
        def input = newExecutionInput().query().build()

        then:
        def assEx = thrown(AssertException)
        assEx.message.contains("query can't be null")


    }

    def "default argument values are respected when variable is not provided"() {
        given:
        def spec = """type Query {
            sayHello(name: String = "amigo"): String
        }"""
        def df = { dfe ->
            return dfe.getArgument("name")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "amigo"]

    }

    def "default variable values are respected"() {
        given:
        def spec = """type Query {
            sayHello(name: String): String
        }"""
        def df = { dfe ->
            return dfe.getArgument("name")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String = "amigo"){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "amigo"]

    }

    def "default variable values are respected for non null arguments"() {
        given:
        def spec = """type Query {
            sayHello(name: String!): String
        }"""
        def df = { dfe ->
            return dfe.getArgument("name")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String! = "amigo"){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "amigo"]

    }

    def "null as default variable value is used"() {
        given:
        def spec = """type Query {
            sayHello(name: String): String
        }"""
        def df = { dfe ->
            boolean isNullValue = dfe.containsArgument("name") && dfe.getArgument("name") == null
            return isNullValue ? "is null" : "error"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String = null){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "is null"]

    }

    def "null as default argument value is used with no provided variable"() {
        given:
        def spec = """type Query {
            sayHello(name: String = null): String
        }"""
        def df = { dfe ->
            boolean isNullValue = dfe.containsArgument("name") && dfe.getArgument("name") == null
            return isNullValue ? "is null" : "error"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "is null"]

    }

    def "not provided variable results in not provided argument"() {
        given:
        def spec = """type Query {
            sayHello(name: String): String
        }"""
        def df = { dfe ->
            return !dfe.containsArgument("name") ? "not provided" : "error"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def data = graphQL.execute('query($var:String){sayHello(name:$var)}').getData()

        then:
        data == [sayHello: "not provided"]

    }

    def "null variable default value produces error for non null argument"() {
        given:
        def spec = """type Query {
            sayHello(name: String!): String
        }"""
        def df = { dfe ->
            return dfe.getArgument("name")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def errors = graphQL.execute('query($var:String=null){sayHello(name:$var)}').getErrors()

        then:
        errors.size() == 1

    }

    def "default value defined in the schema is used when none provided in the query"() {
        // Spec (https://spec.graphql.org/June2018/#sec-All-Variable-Usages-are-Allowed): A notable exception to typical variable type compatibility is allowing a variable definition with a nullable type to be provided to a non‐null location as long as either that variable or that location provides a default value.
        given:
        def spec = """type Query {
            sayHello(name: String! = "amigo"): String
        }"""
        def df = { dfe ->
            return dfe.getArgument("name")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('query($var:String){sayHello(name:$var)}')

        then:
        result.errors.isEmpty()
        result.getData() == [sayHello: "amigo"]

    }

    def "specified url can be defined and queried via introspection"() {
        given:
        GraphQLSchema schema = TestUtil.schema('type Query {foo: MyScalar} scalar MyScalar @specifiedBy(url:"myUrl")')

        when:
        def result = GraphQL.newGraphQL(schema).build().execute('{__type(name: "MyScalar") {name specifiedByURL}}').getData()

        then:
        result == [__type: [name: "MyScalar", specifiedByURL: "myUrl"]]
    }

    def "test DFR and CF"() {
        def sdl = 'type Query { f : String } '

        DataFetcher df = { env ->

            def dfr = DataFetcherResult.newResult().data("hi").build()
            return CompletableFuture.supplyAsync({ -> dfr })
        }
        def schema = TestUtil.schema(sdl, [Query: [f: df]])
        def graphQL = GraphQL.newGraphQL(schema).build()
        when:
        def er = graphQL.execute("{f}")
        then:
        er.data["f"] == "hi"
    }


    def "can set default fetcher exception handler"() {


        def sdl = 'type Query { f : String } '

        DataFetcher df = { env ->
            throw new RuntimeException("BANG!")
        }
        def capturedMsg = null
        def exceptionHandler = new DataFetcherExceptionHandler() {
            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters params) {
                capturedMsg = params.exception.getMessage()
                return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().build())
            }
        }
        def schema = TestUtil.schema(sdl, [Query: [f: df]])
        def graphQL = GraphQL.newGraphQL(schema).defaultDataFetcherExceptionHandler(exceptionHandler).build()
        when:
        graphQL.execute("{f}")
        then:
        capturedMsg == "BANG!"
    }

    def "invalid argument literal"() {
        def sdl = '''
            type Query {
                foo(arg: Input): String
            }
            input Input {
                required: String!
            }
        '''

        def schema = TestUtil.schema(sdl)
        def graphQL = GraphQL.newGraphQL(schema).build()
        when:
        def executionResult = graphQL.execute("{foo(arg:{})}")
        then:
        executionResult.errors.size() == 1
        executionResult.errors[0].message.contains("is missing required fields")
    }

    def "invalid default value for argument via SDL"() {
        given:
        def sdl = '''
            type Query {
                foo(arg: Input = {}): String
            }
            input Input {
                required: String!
            }
        '''
        when:
        def schema = TestUtil.schema(sdl)
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid default value")
    }

    def "invalid default value for argument programmatically"() {
        given:
        def arg = newArgument().name("arg").type(GraphQLInt).defaultValueProgrammatic(new LinkedHashMap()).build()
        def field = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .argument(arg)
                .build()
        when:
        newSchema().query(
                newObject()
                        .name("Query")
                        .field(field)
                        .build())
                .build()
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid default value")
    }

    def "invalid default value for input objects via SDL"() {
        given:
        def sdl = '''
            type Query {
                foo(arg: Input ={required: null}): String
            }
            input Input {
                required: String!
            }
        '''
        when:
        def schema = TestUtil.schema(sdl)
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid default value")
    }

    def "invalid default value for input object programmatically"() {
        given:
        def defaultValue = [required: null]
        def inputObject = newInputObject().name("Input").field(
                newInputObjectField().name("required").type(GraphQLNonNull.nonNull(GraphQLString)).build())
                .build()
        def arg = newArgument().name("arg")
                .type(inputObject)
                .defaultValueProgrammatic(defaultValue).build()
        def field = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .argument(arg)
                .build()
        when:
        newSchema().query(
                newObject()
                        .name("Query")
                        .field(field)
                        .build())
                .build()
        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid default value")
    }

    def "Applied schema directives arguments are validated for SDL"() {
        given:
        def sdl = '''
        directive @cached(
          key: String 
        ) on FIELD_DEFINITION 

        type Query {
          hello: String @cached(key: {foo: "bar"}) 
        }
        '''
        when:
        SchemaGenerator.createdMockedSchema(sdl)
        then:
        def e = thrown(SchemaProblem)
        e.message.contains("an illegal value for the argument ")
    }

    def "getters work as expected"() {
        Instrumentation instrumentation = new SimplePerformantInstrumentation()
        when:
        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).instrumentation(instrumentation).build()
        then:
        graphQL.getGraphQLSchema() == StarWarsSchema.starWarsSchema
        graphQL.getIdProvider() == ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER
        graphQL.getValueUnboxer() == ValueUnboxer.DEFAULT
        graphQL.getPreparsedDocumentProvider() == NoOpPreparsedDocumentProvider.INSTANCE
        graphQL.getInstrumentation() instanceof Instrumentation
        graphQL.getQueryStrategy() instanceof AsyncExecutionStrategy
        graphQL.getMutationStrategy() instanceof AsyncSerialExecutionStrategy
        graphQL.getSubscriptionStrategy() instanceof SubscriptionExecutionStrategy
    }

    def "null locale on input is handled under the covers"() {

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build()
        def ei = newExecutionInput("query q { validationError } ").locale(null).build()

        when:
        def er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
    }

    def "max result nodes not breached"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        def df = { env -> "world" } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello h1: hello h2: hello h3: hello } "
        def ei = newExecutionInput(query).build()
        ei.getGraphQLContext().put(MAX_RESULT_NODES, 4);

        when:
        def er = graphQL.execute(ei)
        def rni = ei.getGraphQLContext().get(ResultNodesInfo.RESULT_NODES_INFO) as ResultNodesInfo
        then:
        !rni.maxResultNodesExceeded
        rni.resultNodesCount == 4
        er.data == [hello: "world", h1: "world", h2: "world", h3: "world"]
    }

    def "max result nodes breached"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        def df = { env -> "world" } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello h1: hello h2: hello h3: hello } "
        def ei = newExecutionInput(query).build()
        ei.getGraphQLContext().put(MAX_RESULT_NODES, 3);

        when:
        def er = graphQL.execute(ei)
        def rni = ei.getGraphQLContext().get(ResultNodesInfo.RESULT_NODES_INFO) as ResultNodesInfo
        then:
        rni.maxResultNodesExceeded
        rni.resultNodesCount == 4
        er.data == [hello: "world", h1: "world", h2: "world", h3: null]
    }

    def "max result nodes breached with list"() {
        given:
        def sdl = '''

        type Query {
          hello: [String]
        }
        '''
        def df = { env -> ["w1", "w2", "w3"] } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello}"
        def ei = newExecutionInput(query).build()
        ei.getGraphQLContext().put(MAX_RESULT_NODES, 3);

        when:
        def er = graphQL.execute(ei)
        def rni = ei.getGraphQLContext().get(ResultNodesInfo.RESULT_NODES_INFO) as ResultNodesInfo
        then:
        rni.maxResultNodesExceeded
        rni.resultNodesCount == 4
        er.data == [hello: null]
    }

    def "max result nodes breached with list 2"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        type Foo {
            name: String
        }
        '''
        def df = { env -> [[name: "w1"], [name: "w2"], [name: "w3"]] } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello {name}}"
        def ei = newExecutionInput(query).build()
        // we have 7 result nodes overall
        ei.getGraphQLContext().put(MAX_RESULT_NODES, 6);

        when:
        def er = graphQL.execute(ei)
        def rni = ei.getGraphQLContext().get(ResultNodesInfo.RESULT_NODES_INFO) as ResultNodesInfo
        then:
        rni.resultNodesCount == 7
        rni.maxResultNodesExceeded
        er.data == [hello: [[name: "w1"], [name: "w2"], [name: null]]]
    }

    def "max result nodes not breached with list"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        type Foo {
            name: String
        }
        '''
        def df = { env -> [[name: "w1"], [name: "w2"], [name: "w3"]] } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello {name}}"
        def ei = newExecutionInput(query).build()
        // we have 7 result nodes overall
        ei.getGraphQLContext().put(MAX_RESULT_NODES, 7);

        when:
        def er = graphQL.execute(ei)
        def rni = ei.getGraphQLContext().get(ResultNodesInfo.RESULT_NODES_INFO) as ResultNodesInfo
        then:
        !rni.maxResultNodesExceeded
        rni.resultNodesCount == 7
        er.data == [hello: [[name: "w1"], [name: "w2"], [name: "w3"]]]
    }

    def "exceptions thrown are turned into graphql errors"() {
        def sdl = """
            type Query {
                f(arg : Boolean) : String
            }
        """

        def graphQL = TestUtil.graphQL(sdl).build()

        when:
        def ei = newExecutionInput("query badSyntax {").build()
        def er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message.contains("Invalid syntax with offending token")


        when:

        ei = newExecutionInput('query badInput($varX : Boolean) { f(arg : $varX) }')
                .variables([varX: "bad"]).build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message.contains("Variable 'varX' has an invalid value")

        when:

        ei = newExecutionInput("query ok1 { f } query ok2 { f  } ")
                .operationName("X").build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message.contains("Unknown operation named 'X'")
    }
}
