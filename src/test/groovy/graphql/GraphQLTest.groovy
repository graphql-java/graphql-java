package graphql

import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.ExecutionInput.Builder
import static graphql.ExecutionInput.newExecutionInput
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

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
        errors[0].sourceLocations == [new SourceLocation(1, 8)]
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
        errors[0].sourceLocations == [new SourceLocation(1, 7)]
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
                        .type(new GraphQLNonNull(GraphQLString))))
                        .build()
        ).build()

        when:
        def errors = GraphQL.newGraphQL(schema).build().execute('{ field }').errors

        then:
        errors.size() == 1
        errors[0].errorType == ErrorType.ValidationError
        errors[0].validationErrorType == ValidationErrorType.MissingFieldArgument
        errors[0].sourceLocations == [new SourceLocation(1, 3)]
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
                .type(new GraphQLList(GraphQLString))
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
        result.errors[0].class == MutationNotSupportedError
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
        result.errors[0].description.contains("has wrong type")
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
                .field(newInputObjectField().name("list").type(new GraphQLList(GraphQLString)).build())
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
                .field(newInputObjectField().name("list").type(new GraphQLList(GraphQLString)).build())
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
                .field(newInputObjectField().name("list").type(new GraphQLList(GraphQLString)).build())
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

}
