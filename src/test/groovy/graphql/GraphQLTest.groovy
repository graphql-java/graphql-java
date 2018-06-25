package graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionId
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.MissingRootTypeException
import graphql.execution.batched.BatchedExecutionStrategy
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.function.UnaryOperator

import static graphql.ExecutionInput.Builder
import static graphql.ExecutionInput.newExecutionInput
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class GraphQLTest extends Specification {

    GraphQLSchema simpleSchema() {
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .staticValue("world")
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build()
        ).build()
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
                .staticValue([id: '123', name: 'homer'])

        GraphQLSchema graphQLSchema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(simpsonField)
                        .build()
        ).build()

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
                .staticValue("world")
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build()
        ).build()

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
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello(() }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.InvalidSyntax
        errors[0].locations == [new SourceLocation(1, 8)]
    }

    def "query with invalid syntax 2"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ hello[](() }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.InvalidSyntax
        errors[0].locations == [new SourceLocation(1, 7)]
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

        def schema = newSchema()
                .query(newObject()
                .name("QueryType")
                .field(newFieldDefinition()
                .name("set")
                .type(list(GraphQLString))
                .dataFetcher({ set })))
                .build()

        when:
        def data = GraphQL.newGraphQL(schema).build().execute("query { set }").data

        then:
        data == [set: ['One', 'Two']]
    }

    def "document with two operations executes specified operation"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition().name("field1").type(GraphQLString).dataFetcher(new StaticDataFetcher("value1")))
                        .field(newFieldDefinition().name("field2").type(GraphQLString).dataFetcher(new StaticDataFetcher("value2")))
        )
                .build()

        def query = """
        query Query1 { field1 }
        query Query2 { field2 }
        """

        def expected = [field2: 'value2']

        when:
        def executionInput = newExecutionInput().query(query).operationName('Query2').context(null).variables([:])
        def result = GraphQL.newGraphQL(schema).build().execute(executionInput)

        then:
        result.data == expected
        result.errors.size() == 0
    }

    def "document with two operations but no specified operation throws"() {
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
        GraphQL.newGraphQL(schema).build().execute(query)

        then:
        thrown(GraphQLException)
    }

    def "null mutation type does not throw an npe re: #345 but returns and error"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("Query")
        )
                .build()

        when:
        def result = new GraphQL(schema).execute("mutation { doesNotExist }")

        then:
        result.errors.size() == 1
        result.errors[0].class == MissingRootTypeException
    }

    def "#875 a subscription query against a schema that doesn't support subscriptions should result in a GraphQL error"() {
        given:

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("Query")
        )
                .build()

        when:
        def result = new GraphQL(schema).execute("subscription { doesNotExist }")

        then:
        result.errors.size() == 1
        result.errors[0].class == MissingRootTypeException
    }

    def "query with int literal too large"() {
        given:
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build())
                                .dataFetcher({ return it.getArgument("bar") })
                ))
                .build()
        def query = "{foo(bar: 12345678910)}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 1
        result.errors[0].description == "argument 'bar' with value 'IntValue{value=12345678910}' is not a valid 'Int'"
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with missing argument results in arguments map missing the key"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo}"
        when:
        GraphQL.newGraphQL(schema).build().execute(query)

        then:
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                assert !env.arguments.containsKey('bar')
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with null argument results in arguments map with value null "() {
        given:
        def dataFetcher = Mock(DataFetcher)
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(GraphQLInt).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: null)}"
        DataFetchingEnvironment dataFetchingEnvironment
        when:
        GraphQL.newGraphQL(schema).build().execute(query)

        then:
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                dataFetchingEnvironment = env
                assert env.arguments.containsKey('bar')
                assert env.arguments['bar'] == null
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with missing key in an input object result in a map with missing key"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("someKey").type(GraphQLString).build())
                .field(newInputObjectField().name("otherKey").type(GraphQLString).build()).build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: {someKey: \"value\"})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                assert env.arguments.size() == 1
                assert env.arguments["bar"] instanceof Map
                assert env.arguments['bar']['someKey'] == 'value'
                assert !(env.arguments['bar'] as Map).containsKey('otherKey')
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "query with null value in an input object result in a map with null as value"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("someKey").type(GraphQLString).build())
                .field(newInputObjectField().name("otherKey").type(GraphQLString).build()).build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: {someKey: \"value\", otherKey: null})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                assert env.arguments.size() == 1
                assert env.arguments["bar"] instanceof Map
                assert env.arguments['bar']['someKey'] == 'value'
                assert (env.arguments['bar'] as Map).containsKey('otherKey')
                assert env.arguments['bar']['otherKey'] == null
        }
    }

    def "query with missing List input field results in a map with a missing key"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: {})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                assert env.arguments.size() == 1
                assert env.arguments["bar"] instanceof Map
                assert !(env.arguments['bar'] as Map).containsKey('list')
        }
    }

    def "query with null List input field results in a map with null as key"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: {list: null})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
                assert env.arguments.size() == 1
                assert env.arguments["bar"] instanceof Map
                assert (env.arguments['bar'] as Map).containsKey('list')
                assert env.arguments['bar']['list'] == null
        }
    }

    def "query with List containing null input field results in a map with a list containing null"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def inputObject = newInputObject().name("bar")
                .field(newInputObjectField().name("list").type(list(GraphQLString)).build())
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(
                        newFieldDefinition()
                                .name("foo")
                                .type(GraphQLInt)
                                .argument(newArgument().name("bar").type(inputObject).build())
                                .dataFetcher(dataFetcher)
                ))
                .build()
        def query = "{foo(bar: {list: [null]})}"
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(query)

        then:
        result.errors.size() == 0
        1 * dataFetcher.get(_) >> {
            DataFetchingEnvironment env ->
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
                .typeResolver({ type -> foo })
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

        GraphQLSchema schema = newSchema()
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
        result.errors[0].message.contains("Sub selection required")

        where:
        instrumentationName    | instrumentation
        'max query depth'      | new MaxQueryDepthInstrumentation(10)
        'max query complexity' | new MaxQueryComplexityInstrumentation(10)
    }


    def "batched execution with non batched DataFetcher returning CompletableFuture"() {
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
                .typeResolver(
                {
                    env ->
                        if (env.getObject() instanceof CompletableFuture) {
                            throw new RuntimeException("This seems bad!")
                        }

                        return foo
                })
                .build()

        GraphQLObjectType query = newObject()
                .name("RootQuery")
                .field(
                { field ->
                    field
                            .name("node")
                            .dataFetcher(
                            { env ->
                                CompletableFuture.supplyAsync({ ->
                                    Map<String, String> map = new HashMap<>()
                                    map.put("id", "abc")

                                    return map
                                })
                            })
                            .type(node)
                } as UnaryOperator)
                .build()

        GraphQLSchema schema = newSchema()
                .query(query)
                .additionalType(foo)
                .build()

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new BatchedExecutionStrategy())
                .mutationExecutionStrategy(new BatchedExecutionStrategy())
                .build()

        ExecutionInput executionInput = newExecutionInput()
                .query("{node {id}}")
                .build()
        when:
        def result = graphQL
                .execute(executionInput)

        then:
        result.getData() == [node: [id: "abc"]]
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

        def instrumentation = new SimpleInstrumentation()
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
        queryStrategy.instrumentation == instrumentation

        when:

        // now make some changes
        def newInstrumentation = new SimpleInstrumentation()
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
        queryStrategy.instrumentation == newInstrumentation
    }

    def "query with triple quoted multi line strings"() {
        given:
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .argument(newArgument().name("arg").type(GraphQLString))
                .dataFetcher({ env -> env.getArgument("arg") }
        )
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("Query")
                        .field(fieldDefinition)
                        .build()
        ).build()

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
}
