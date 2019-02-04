package graphql.execution

import graphql.Assert
import graphql.DataFetchingErrorGraphQLError
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.Scalars
import graphql.SerializationError
import graphql.StarWarsSchema
import graphql.TypeMismatchError
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import static ExecutionStrategyParameters.newParameters
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mergedField
import static graphql.TestUtil.mergedSelectionSet
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject

@SuppressWarnings("GroovyPointlessBoolean")
class ExecutionStrategyTest extends Specification {


    DataFetcherExceptionHandler dataFetcherExceptionHandler = new SimpleDataFetcherExceptionHandler()
    ExecutionStrategy executionStrategy

    def setup() {
        executionStrategy = new ExecutionStrategy(dataFetcherExceptionHandler) {

            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
                return Assert.assertShouldNeverHappen("should not be called")
            }
        }
    }


    def buildContext(GraphQLSchema schema = null) {
        ExecutionId executionId = ExecutionId.from("executionId123")
        def variables = [arg1: "value1"]
        new ExecutionContext(SimpleInstrumentation.INSTANCE, executionId, schema ?: StarWarsSchema.starWarsSchema, null,
                executionStrategy, executionStrategy, executionStrategy,
                null, null, null,
                variables, "context", "root", new DataLoaderRegistry(), Collections.emptyList())
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "complete values always calls query strategy to execute more"() {
        given:
        def dataFetcher = Mock(DataFetcher)
        def fieldDefinition = newFieldDefinition()
                .name("someField")
                .type(GraphQLString)
                .dataFetcher(dataFetcher)
                .build()
        def objectType = newObject()
                .name("Test")
                .field(fieldDefinition)
                .build()

        def document = new Parser().parseDocument("{someField}")
        def operation = document.definitions[0] as OperationDefinition

        GraphQLSchema schema = GraphQLSchema.newSchema().query(objectType).build()
        def builder = new ExecutionContextBuilder()
        builder.queryStrategy(Mock(ExecutionStrategy))
        builder.mutationStrategy(Mock(ExecutionStrategy))
        builder.subscriptionStrategy(Mock(ExecutionStrategy))
        builder.graphQLSchema(schema)

        builder.operationDefinition(operation)
        builder.executionId(ExecutionId.generate())

        def executionContext = builder.build()
        def result = new Object()
        def parameters = newParameters()
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo().type(objectType))
                .source(result)
                .fields(mergedSelectionSet(["fld": [Field.newField().build()]]))
                .field(mergedField(Field.newField().build()))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters)

        then:
        1 * executionContext.queryStrategy.execute(_, _)
        0 * executionContext.mutationStrategy.execute(_, _)
        0 * executionContext.subscriptionStrategy.execute(_, _)
    }


    def "completes value for a java.util.List"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = list(GraphQLString)
        Field field = new Field("someField")
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ExecutionPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)


        def result = ["test", "1", "2", "3"]
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": []]))
                .field(mergedField(field))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == result
    }

    def "completes value for java.util.Optional"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = GraphQLString
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == expected

        where:
        result                    || expected
        Optional.of("hello")      || "hello"
        Optional.ofNullable(null) || null
    }

    def "completes value for an empty java.util.Optional that triggers non null exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = nonNull(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(Optional.ofNullable(null))
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        def e = thrown(CompletionException)
        e.getCause() instanceof NonNullableFieldWasNullException
    }

    def "completes value for java.util.OptionalInt"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = GraphQLString
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == expected

        where:
        result              || expected
        OptionalInt.of(10)  || "10"
        OptionalInt.empty() || null
    }

    def "completes value for an empty java.util.OptionalInt that triggers non null exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = nonNull(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalInt.empty())
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        def e = thrown(CompletionException)
        e.getCause() instanceof NonNullableFieldWasNullException
    }

    def "completes value for java.util.OptionalDouble"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = GraphQLString
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == expected

        where:
        result                 || expected
        OptionalDouble.of(10)  || "10.0"
        OptionalDouble.empty() || null
    }

    def "completes value for an empty java.util.OptionalDouble that triggers non null exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = nonNull(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalDouble.empty())
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        def e = thrown(CompletionException)
        e.getCause() instanceof NonNullableFieldWasNullException
    }

    def "completes value for java.util.OptionalLong"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = GraphQLString
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == expected

        where:
        result               || expected
        OptionalLong.of(10)  || "10"
        OptionalLong.empty() || null
    }

    def "completes value for an empty java.util.OptionalLong that triggers non null exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = nonNull(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalLong.empty())
                .fields(mergedSelectionSet  (["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        def e = thrown(CompletionException)
        e.getCause() instanceof NonNullableFieldWasNullException
    }

    def "completes value for an array"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = list(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ExecutionPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)
        def result = ["test", "1", "2", "3"]
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": []]))
                .field(mergedField(new Field("someField")))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == result
    }

    def "completing value with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = Scalars.GraphQLInt
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        String result = "not a number"

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["dummy": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof SerializationError

    }

    def "completing enum with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        GraphQLEnumType enumType = newEnum().name("Enum").value("value").build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(enumType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        String result = "not a enum number"

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["dummy": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof SerializationError

    }

    def "completing a scalar null value for a non null type throws an exception"() {

        GraphQLScalarType NullProducingScalar = new GraphQLScalarType("Custom", "It Can Produce Nulls", new Coercing<Double, Double>() {
            @Override
            Double serialize(Object input) {
                if (input == 0xCAFED00Dd) {
                    return null
                }
                return 0xCAFEBABEd
            }

            @Override
            Double parseValue(Object input) {
                throw new UnsupportedOperationException("Not implemented")
            }

            @Override
            Double parseLiteral(Object input) {
                throw new UnsupportedOperationException("Not implemented")
            }
        })


        ExecutionContext executionContext = buildContext()
        def fieldType = NullProducingScalar
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(nonNull(fieldType)).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)

        when:
        def parameters = newParameters()
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo().type(fieldType))
                .source(result)
                .fields(mergedSelectionSet(["dummy": []]))
                .nonNullFieldValidator(nullableFieldValidator)
                .build()

        Exception actualException = null
        try {
            executionStrategy.completeValue(executionContext, parameters)
        } catch (Exception e) {
            actualException = e
        }

        then:
        if (errorExpected) {
            actualException instanceof NonNullableFieldWasNullException
            executionContext.errors.size() == 1
        } else {
            actualException != null
            executionContext.errors.size() == 0
        }


        where:
        result      || errorExpected
        1.0d        || false
        0xCAFED00Dd || true
        null        || true
    }

    @SuppressWarnings("GroovyVariableNotAssigned")
    def "resolveField creates correct DataFetchingEnvironment"() {
        def dataFetcher = Mock(DataFetcher)
        def fieldDefinition = newFieldDefinition()
                .name("someField")
                .type(GraphQLString)
                .dataFetcher(dataFetcher)
                .argument(newArgument().name("arg1").type(GraphQLString))
                .build()
        def objectType = newObject()
                .name("Test")
                .field(fieldDefinition)
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema().query(objectType).build()
        ExecutionContext executionContext = buildContext(schema)
        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        Argument argument = new Argument("arg1", new StringValue("argVal"))
        Field field = new Field("someField", [argument])
        ExecutionPath executionPath = ExecutionPath.rootPath().segment("test")

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source("source")
                .fields(mergedSelectionSet(["someField": [field]]))
                .field(mergedField(field))
                .nonNullFieldValidator(nullableFieldValidator)
                .path(executionPath)
                .build()
        DataFetchingEnvironment environment

        when:
        executionStrategy.resolveField(executionContext, parameters)

        then:
        1 * dataFetcher.get({ it -> environment = it } as DataFetchingEnvironment)
        environment.fieldDefinition == fieldDefinition
        environment.graphQLSchema == schema
        environment.context == "context"
        environment.source == "source"
        environment.fields == [field]
        environment.root == "root"
        environment.parentType == objectType
        environment.arguments == ["arg1": "argVal"]
        environment.executionStepInfo.getUnwrappedNonNullType() == GraphQLString
        environment.executionStepInfo.path == executionPath
        environment.executionStepInfo.parent.getUnwrappedNonNullType() == objectType
        environment.executionId == ExecutionId.from("executionId123")
    }

    def exceptionSetupFixture(expectedException) {
        def dataFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                throw expectedException
            }
        }
        def fieldDefinition = newFieldDefinition().name("someField").type(GraphQLString).dataFetcher(dataFetcher).build()
        def objectType = newObject()
                .name("Test")
                .field(fieldDefinition)
                .build()
        def schema = GraphQLSchema.newSchema().query(objectType).build()
        ExecutionContext executionContext = buildContext(schema)
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        ExecutionPath expectedPath = ExecutionPath.rootPath().segment("someField")

        SourceLocation sourceLocation = new SourceLocation(666, 999)
        Field field = Field.newField("someField").sourceLocation(sourceLocation).build()
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source("source")
                .fields(mergedSelectionSet(["someField": [field]]))
                .field(mergedField(field))
                .path(expectedPath)
                .nonNullFieldValidator(nullableFieldValidator)
                .build()
        [executionContext, fieldDefinition, expectedPath, parameters, field, sourceLocation]
    }


    def "test that the new data fetcher error handler interface is called"() {

        def expectedException = new UnsupportedOperationException("This is the exception you are looking for")

        //noinspection GroovyAssignabilityCheck,GroovyUnusedAssignment
        def (ExecutionContext executionContext, GraphQLFieldDefinition fieldDefinition, ExecutionPath expectedPath, ExecutionStrategyParameters parameters, Field field, SourceLocation sourceLocation) = exceptionSetupFixture(expectedException)


        boolean handlerCalled = false
        ExecutionStrategy overridingStrategy = new AsyncExecutionStrategy(new SimpleDataFetcherExceptionHandler() {
            @Override
            DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
                handlerCalled = true
                assert handlerParameters.exception == expectedException
                assert handlerParameters.fieldDefinition == fieldDefinition
                assert handlerParameters.field.name == 'someField'
                assert handlerParameters.path == expectedPath

                // by calling down we are testing the base class as well
                super.onException(handlerParameters)
            }
        }) {
            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext ec, ExecutionStrategyParameters p) throws NonNullableFieldWasNullException {
                null
            }
        }

        when:
        overridingStrategy.resolveField(executionContext, parameters)

        then:
        handlerCalled == true
        executionContext.errors.size() == 1
        def exceptionWhileDataFetching = executionContext.errors[0] as ExceptionWhileDataFetching
        exceptionWhileDataFetching.getLocations() == [sourceLocation]
        exceptionWhileDataFetching.getMessage().contains('This is the exception you are looking for')
    }

    def "test that the old legacy method is still useful for those who derive new execution strategies"() {

        def expectedException = new UnsupportedOperationException("This is the exception you are looking for")

        //noinspection GroovyAssignabilityCheck,GroovyUnusedAssignment
        def (ExecutionContext executionContext, GraphQLFieldDefinition fieldDefinition, ExecutionPath expectedPath, ExecutionStrategyParameters parameters, Field field, SourceLocation sourceLocation) = exceptionSetupFixture(expectedException)


        ExecutionStrategy overridingStrategy = new ExecutionStrategy() {
            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext ec, ExecutionStrategyParameters p) throws NonNullableFieldWasNullException {
                null
            }
        }

        when:
        overridingStrategy.resolveField(executionContext, parameters)

        then:
        executionContext.errors.size() == 1
        def exceptionWhileDataFetching = executionContext.errors[0] as ExceptionWhileDataFetching
        exceptionWhileDataFetching.getMessage().contains('This is the exception you are looking for')
    }

    def "#522 - single error during execution - not two errors"() {

        given:

        def dataFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                throw new RuntimeException("bang")
            }
        }
        def fieldDefinition = newFieldDefinition()
                .name("someField")
                .type(nonNull(GraphQLString))
                .dataFetcher(dataFetcher)
                .build()
        def objectType = newObject()
                .name("Test")
                .field(fieldDefinition)
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema().query(objectType).build()
        ExecutionContext executionContext = buildContext(schema)

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)
        Field field = new Field("someField")

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(null)
                .nonNullFieldValidator(nullableFieldValidator)
                .field(mergedField(field))
                .fields(mergedSelectionSet(["someField": [mergedField(field)]]))
                .path(ExecutionPath.rootPath().segment("abc"))
                .build()

        when:
        executionStrategy.resolveField(executionContext, parameters).join()

        then:
        thrown(CompletionException)
        executionContext.errors.size() == 1 // only 1 error
        executionContext.errors[0] instanceof ExceptionWhileDataFetching
    }

    def "#163 completes value for an primitive type array"() {
        given:
        ExecutionContext executionContext = buildContext()
        long[] result = [1L, 2L, 3L]
        def fieldType = list(Scalars.GraphQLLong)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ExecutionPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().build())]]))
                .field(mergedField(Field.newField().build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue

        then:
        executionResult.get().data == [1L, 2L, 3L]
    }

    def "#820 processes DataFetcherResult"() {
        given:

        ExecutionContext executionContext = buildContext()
        def fieldType = list(Scalars.GraphQLLong)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        def field = Field.newField("parent").sourceLocation(new SourceLocation(5, 10)).build()
        def parameters = newParameters()
                .path(ExecutionPath.fromList(["parent"]))
                .field(mergedField(field))
                .fields(mergedSelectionSet(["parent": [mergedField(field)]]))
                .executionStepInfo(executionStepInfo)
                .build()

        def executionData = ["child": [:]]
        when:
        def fetchedValue = executionStrategy.unboxPossibleDataFetcherResult(executionContext, parameters,
                DataFetcherResult.newResult().data(executionData)
                        .mapRelativeErrors(true)
                        .error(new DataFetchingErrorGraphQLError("bad foo", ["child", "foo"]))
                        .build()
        )

        then:
        fetchedValue.getFetchedValue() == executionData
        executionContext.getErrors()[0].locations == [new SourceLocation(7, 20)]
        executionContext.getErrors()[0].message == "bad foo"
        executionContext.getErrors()[0].path == ["parent", "child", "foo"]
    }

    def "#820 processes DataFetcherResult just message"() {
        given:

        ExecutionContext executionContext = buildContext()
        def fieldType = list(Scalars.GraphQLLong)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        def field = Field.newField("parent").sourceLocation(new SourceLocation(5, 10)).build()
        def parameters = newParameters()
                .path(ExecutionPath.fromList(["parent"]))
                .field(mergedField(field))
                .fields(mergedSelectionSet(["parent": [mergedField(field)]]))
                .executionStepInfo(executionStepInfo)
                .build()

        def executionData = ["child": [:]]
        when:
        def fetchedValue = executionStrategy.unboxPossibleDataFetcherResult(executionContext, parameters,
                DataFetcherResult.newResult().data(executionData)
                        .error(new DataFetchingErrorGraphQLError("bad foo"))
                        .build())
        then:
        fetchedValue.getFetchedValue() == executionData
        executionContext.getErrors()[0].locations == null
        executionContext.getErrors()[0].message == "bad foo"
        executionContext.getErrors()[0].path == null
    }

    def "completes value for an iterable"() {
        given:
        ExecutionContext executionContext = buildContext()
        List<Long> result = [1L, 2L, 3L]
        def fieldType = list(Scalars.GraphQLLong)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ExecutionPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().build())]]))
                .field(mergedField(Field.newField().build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue

        then:
        executionResult.get().data == [1L, 2L, 3L]
    }

    def "when completeValue expects GraphQLList and non iterable or non array is passed then it should yield a TypeMismatch error"() {
        given:
        ExecutionContext executionContext = buildContext()
        Map<String, Object> result = new HashMap<>()
        def fieldType = list(Scalars.GraphQLLong)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo)

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().build())]]))
                .field(mergedField(Field.newField().build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValue.join()

        then:
        executionResult.data == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof TypeMismatchError
    }
}
