package graphql

import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

/**
 * A set of tests to show how non null field handling correctly bubble up or not
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class NonNullHandlingTest extends Specification {

    class SimpleObject {
        String nullChild = null
        String nonNullChild = "not null"
        List<String> nonNullListWithNull = ["foo", null, "bar"]
        List<String> nullableListWithNull = ["foo", null, "bar"]
    }

    class ContainingObject {
        SimpleObject nullParent = null
        SimpleObject nonNullParent = new SimpleObject()
    }

    static def executionInput(String query) {
        ExecutionInput.newExecutionInput().query(query).build()
    }

    @Unroll
    def "#268 - null child field values are allowed in nullable parent type (strategy: #strategyName)"() {
        // see https://github.com/graphql-java/graphql-java/issues/268

        given:
        def rootTypeName = "RootQueryType"
        def parentFieldName = "parent"
        GraphQLOutputType parentType = newObject()
                .name("currentType")
                .field(newFieldDefinition()
                        .name("nullChild")
                        .type(nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nonNullChild")
                        .type(nonNull(GraphQLString)))
                .build()

        def parentCoordinates = FieldCoordinates.coordinates(rootTypeName, parentFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new SimpleObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(parentCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(newFieldDefinition()
                                .name(parentFieldName)
                                .type(parentType) // nullable parent
                        )
                ).build()

        def query = """
        query { 
            parent {
                nonNullChild
                nullChild
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result.errors.size() == 1
        result.data["parent"] == null

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }

    @Unroll
    def "#268 - null child field values are NOT allowed in non nullable parent types (strategy: #strategyName)"() {
        // see https://github.com/graphql-java/graphql-java/issues/268

        given:
        def rootTypeName = "RootQueryType"
        def parentFieldName = "parent"

        GraphQLOutputType parentType = newObject()
                .name("currentType")
                .field(newFieldDefinition().name("nullChild")
                        .type(nonNull(GraphQLString)))
                .field(newFieldDefinition().name("nonNullChild")
                        .type(nonNull(GraphQLString)))
                .build()

        def parentCoordinates = FieldCoordinates.coordinates(rootTypeName, parentFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new SimpleObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(parentCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(
                                newFieldDefinition()
                                        .name(parentFieldName)
                                        .type(nonNull(parentType)) // non nullable parent
                        )
                ).build()

        def query = """
        query { 
            parent {
                nonNullChild
                nullChild
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result.errors.size() == 1
        result.data == null

        where:

        strategyName  | executionStrategy
//        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
//        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }

    @Unroll
    def "#581 - null child field values are allowed in nullable grand parent type (strategy: #strategyName)"() {
        given:

        def rootTypeName = "RootQueryType"
        def topFieldName = "top"

        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition()
                        .name("nullChild")
                        .type(nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nonNullChild")
                        .type(nonNull(GraphQLString)))
                .build()

        GraphQLOutputType topType = newObject()
                .name("topType")
                .field(newFieldDefinition()
                        .name("nullParent")
                        .type(nonNull(parentType)))
                .field(newFieldDefinition()
                        .name("nonNullParent")
                        .type(nonNull(parentType)))
                .build()

        def topCoordinates = FieldCoordinates.coordinates(rootTypeName, topFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new ContainingObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(topCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(newFieldDefinition()
                                .name(topFieldName)
                                .type(topType) // nullable grand parent

                        )
                ).build()

        def query = """
        query { 
            top {
                nonNullParent {
                    nonNullChild
                    nullChild
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result.errors.size() == 1
        result.data != null
        result.data["grandParent"] == null

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()

    }

    @Unroll
    def "#581 - null child field values are NOT allowed in non nullable grand parent types (strategy: #strategyName)"() {

        given:
        def rootTypeName = "RootQueryType"
        def topFieldName = "top"

        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition()
                        .name("nullChild")
                        .type(nonNull(GraphQLString)))
                .field(newFieldDefinition()
                        .name("nonNullChild")
                        .type(nonNull(GraphQLString)))
                .build()

        GraphQLOutputType topType = newObject()
                .name("topType")
                .field(newFieldDefinition()
                        .name("nullParent")
                        .type(nonNull(parentType)))
                .field(newFieldDefinition()
                        .name("nonNullParent")
                        .type(nonNull(parentType)))
                .build()

        def topCoordinates = FieldCoordinates.coordinates(rootTypeName, topFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new ContainingObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(topCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(newFieldDefinition()
                                .name(topFieldName)
                                .type(nonNull(topType)) // non nullable grand parent
                        )
                ).build()

        def query = """
        query { 
            top {
                nonNullParent {
                    nonNullChild
                    nullChild
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result != null
        result.errors.size() == 1
        result.data == null

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()

    }

    @Unroll
    def "#561 - null entry in non null list type with non null wrapper list (strategy: #strategyName)"() {

        given:
        def rootTypeName = "RootQueryType"
        def topFieldName = "top"

        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition()
                        .name("nonNullListWithNull")
                        .type(nonNull(list(nonNull(GraphQLString)))))
                .build()

        GraphQLOutputType topType = newObject()
                .name("topType")
                .field(newFieldDefinition()
                        .name("nullParent")
                        .type(nonNull(parentType)))
                .field(newFieldDefinition()
                        .name("nonNullParent")
                        .type(nonNull(parentType)))
                .build()

        def topCoordinates = FieldCoordinates.coordinates(rootTypeName, topFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new ContainingObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(topCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(newFieldDefinition()
                                .name(topFieldName)
                                .type(nonNull(topType)) // non nullable grand parent
                        )
                ).build()

        def query = """
        query { 
            top {
                nonNullParent {
                    nonNullListWithNull
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result != null
        result.errors.size() == 1
        result.data == null

        where:

        strategyName  | executionStrategy
//        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
//        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()

    }

    @Unroll
    def "#561 - null entry in non null list type with nullable wrapper list(strategy: #strategyName)"() {

        given:

        def rootTypeName = "RootQueryType"
        def topFieldName = "top"

        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition()
                        .name("nullableListWithNull")
                        .type(list(nonNull(GraphQLString))))
                .build()

        GraphQLOutputType topType = newObject()
                .name("topType")
                .field(newFieldDefinition()
                        .name("nullParent")
                        .type(nonNull(parentType)))
                .field(newFieldDefinition()
                        .name("nonNullParent")
                        .type(nonNull(parentType)))
                .build()

        def topCoordinates = FieldCoordinates.coordinates(rootTypeName, topFieldName)
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return new ContainingObject()
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(topCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(rootTypeName)
                        .field(newFieldDefinition()
                                .name(topFieldName)
                                .type(nonNull(topType)) // non nullable grand parent
                        )
                ).build()

        def query = """
        query { 
            top {
                nonNullParent {
                    nullableListWithNull
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result != null
        result.data == ["top": ["nonNullParent": ["nullableListWithNull": null]]]
        result.errors.size() == 1

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }
}