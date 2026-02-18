package graphql.execution

import graphql.Assert
import graphql.EngineRunningState
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.GraphqlErrorBuilder
import graphql.Profiler
import graphql.Scalars
import graphql.SerializationError
import graphql.StarWarsSchema
import graphql.TypeMismatchError
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.LightDataFetcher
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.function.Supplier
import java.util.stream.Stream

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
        ExecutionInput ei = ExecutionInput.newExecutionInput("{}").build()
        def variables = [arg1: "value1"]
        def builder = ExecutionContextBuilder.newExecutionContextBuilder()
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .executionId(executionId)
                .graphQLSchema(schema ?: StarWarsSchema.starWarsSchema)
                .queryStrategy(executionStrategy)
                .mutationStrategy(executionStrategy)
                .subscriptionStrategy(executionStrategy)
                .coercedVariables(CoercedVariables.of(variables))
                .graphQLContext(GraphQLContext.newContext().of("key", "context").build())
                .executionInput(ei)
                .root("root")
                .dataLoaderRegistry(new DataLoaderRegistry())
                .locale(Locale.getDefault())
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .profiler(Profiler.NO_OP)
                .engineRunningState(new EngineRunningState(ei, Profiler.NO_OP))

        new ExecutionContext(builder)
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "complete values always calls query strategy to execute more"() {
        given:
        def dataFetcher = Mock(DataFetcher)

        def someFieldName = "someField"
        def testTypeName = "Test"
        def fieldDefinition = newFieldDefinition()
                .name(someFieldName)
                .type(GraphQLString)
                .build()
        def objectType = newObject()
                .name(testTypeName)
                .field(fieldDefinition)
                .build()

        def someFieldCoordinates = FieldCoordinates.coordinates(testTypeName, someFieldName)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(someFieldCoordinates, dataFetcher)
                .build()

        def document = new Parser().parseDocument("{someField}")
        def operation = document.definitions[0] as OperationDefinition

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(objectType)
                .build()

        def builder = new ExecutionContextBuilder()
        builder.queryStrategy(Mock(ExecutionStrategy))
        builder.mutationStrategy(Mock(ExecutionStrategy))
        builder.subscriptionStrategy(Mock(ExecutionStrategy))
        builder.graphQLSchema(schema)
        builder.valueUnboxer(ValueUnboxer.DEFAULT)

        builder.operationDefinition(operation)
        builder.executionId(ExecutionId.generate())
        builder.executionInput(ExecutionInput.newExecutionInput("{}").build())

        def executionContext = builder.build()
        def result = new Object()
        def parameters = newParameters()
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo().type(objectType))
                .source(result)
                .fields(mergedSelectionSet(["fld": [Field.newField().name("dummy").build()]]))
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters)

        then:
        1 * executionContext.queryStrategy.executeObject(_, _) >> CompletableFuture.completedFuture(null)
        0 * executionContext.mutationStrategy.execute(_, _)
        0 * executionContext.subscriptionStrategy.execute(_, _)
    }


    def "completes value for a java.util.List"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = list(GraphQLString)
        Field field = new Field("someField")
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)


        def result = ["test", "1", "2", "3"]
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": []]))
                .field(mergedField(field))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == result
    }

    def "completes value for java.util.Optional"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = GraphQLString
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == expected

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(Optional.ofNullable(null))
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == expected

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalInt.empty())
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == expected

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalDouble.empty())
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(result)
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == expected

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
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .nonNullFieldValidator(nullableFieldValidator)
                .source(OptionalLong.empty())
                .fields(mergedSelectionSet(["fld": []]))
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        def e = thrown(CompletionException)
        e.getCause() instanceof NonNullableFieldWasNullException
    }

    def "completes value for an array"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = list(GraphQLString)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        def result = ["test", "1", "2", "3"]
        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": []]))
                .field(mergedField(new Field("someField")))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == result
    }

    def "completing value with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = Scalars.GraphQLInt
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        String result = "not a number"

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["dummy": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof SerializationError

    }

    def "completing enum with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        GraphQLEnumType enumType = newEnum().name("Enum").value("value").build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(enumType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        String result = "not a enum number"

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["dummy": []]))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof SerializationError

    }

    def "completing a scalar null value for a non null type throws an exception"() {

        GraphQLScalarType NullProducingScalar = GraphQLScalarType.newScalar().name("Custom").description("It Can Produce Nulls").coercing(new Coercing<Double, Double>() {
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
                .build()


        ExecutionContext executionContext = buildContext()
        def fieldType = NullProducingScalar
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(nonNull(fieldType)).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

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
        def dataFetcher = Mock(LightDataFetcher)
        def someFieldName = "someField"
        def testTypeName = "Type"
        def fieldDefinition = newFieldDefinition()
                .name(someFieldName)
                .type(GraphQLString)
                .argument(newArgument().name("arg1").type(GraphQLString))
                .build()
        def objectType = newObject()
                .name(testTypeName)
                .field(fieldDefinition)
                .build()

        def someFieldCoordinates = FieldCoordinates.coordinates(testTypeName, someFieldName)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(someFieldCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(objectType)
                .build()
        ExecutionContext executionContext = buildContext(schema)
        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        Argument argument = new Argument("arg1", new StringValue("argVal"))
        Field field = new Field(someFieldName, [argument])
        MergedField mergedField = mergedField(field)
        ResultPath resultPath = ResultPath.rootPath().segment("test")

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source("source")
                .fields(mergedSelectionSet(["someField": [field]]))
                .field(mergedField)
                .nonNullFieldValidator(nullableFieldValidator)
                .path(resultPath)
                .build()
        DataFetchingEnvironment environment

        when:
        executionStrategy.resolveFieldWithInfo(executionContext, parameters)

        then:
        1 * dataFetcher.get(_, _, _) >> { environment = (it[2] as Supplier<DataFetchingEnvironment>).get() }
        environment.fieldDefinition == fieldDefinition
        environment.graphQLSchema == schema
        environment.graphQlContext.get("key") == "context"
        environment.source == "source"
        environment.mergedField == mergedField
        environment.root == "root"
        environment.parentType == objectType
        environment.arguments == ["arg1": "argVal"]
        environment.executionStepInfo.getUnwrappedNonNullType() == GraphQLString
        environment.executionStepInfo.path == resultPath
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

        def someFieldName = "someField"
        def testTypeName = "Test"
        def fieldDefinition = newFieldDefinition()
                .name(someFieldName)
                .type(GraphQLString)
                .build()
        def objectType = newObject()
                .name(testTypeName)
                .field(fieldDefinition)
                .build()

        def someFieldCoordinates = FieldCoordinates.coordinates(testTypeName, someFieldName)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(someFieldCoordinates, dataFetcher)
                .build()
        def schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(objectType)
                .build()
        ExecutionContext executionContext = buildContext(schema)
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        ResultPath expectedPath = ResultPath.rootPath().segment(someFieldName)

        SourceLocation sourceLocation = new SourceLocation(666, 999)
        Field field = Field.newField(someFieldName).sourceLocation(sourceLocation).build()
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
        def (ExecutionContext executionContext, GraphQLFieldDefinition fieldDefinition, ResultPath expectedPath, ExecutionStrategyParameters parameters, Field field, SourceLocation sourceLocation) = exceptionSetupFixture(expectedException)


        boolean handlerCalled = false
        ExecutionStrategy overridingStrategy = new AsyncExecutionStrategy(new SimpleDataFetcherExceptionHandler() {


            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
                handlerCalled = true
                assert handlerParameters.exception == expectedException
                assert handlerParameters.fieldDefinition == fieldDefinition
                assert handlerParameters.field.name == 'someField'
                assert handlerParameters.path == expectedPath

                // by calling down we are testing the base class as well
                super.handleException(handlerParameters)
            }
        }) {
            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext ec, ExecutionStrategyParameters p) throws NonNullableFieldWasNullException {
                null
            }
        }

        when:
        overridingStrategy.resolveFieldWithInfo(executionContext, parameters)

        then:
        handlerCalled == true
        executionContext.errors.size() == 1
        def exceptionWhileDataFetching = executionContext.errors[0] as ExceptionWhileDataFetching
        exceptionWhileDataFetching.getLocations() == [sourceLocation]
        exceptionWhileDataFetching.getMessage().contains('This is the exception you are looking for')
    }


    def "#2519 data fetcher errors for a given field appear in FetchedResult within instrumentation"() {
        def expectedException = new UnsupportedOperationException("This is the exception you are looking for")

        //noinspection GroovyAssignabilityCheck,GroovyUnusedAssignment
        def (ExecutionContext executionContext, GraphQLFieldDefinition fieldDefinition, ResultPath expectedPath, ExecutionStrategyParameters params, Field field, SourceLocation sourceLocation) = exceptionSetupFixture(expectedException)

        ExecutionContextBuilder executionContextBuilder = ExecutionContextBuilder.newExecutionContextBuilder(executionContext)
        def instrumentation = new SimplePerformantInstrumentation() {
            Map<String, FetchedValue> fetchedValues = [:]

            @Override
            @Override
            InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
                if (parameters.getFetchedObject() instanceof FetchedValue) {
                    FetchedValue value = (FetchedValue) parameters.getFetchedObject()
                    fetchedValues.put(parameters.field.name, value)
                }
                return super.beginFieldCompletion(parameters, state)
            }
        }
        ExecutionContext instrumentedExecutionContext = executionContextBuilder.instrumentation(instrumentation).build()

        ExecutionStrategy overridingStrategy = new ExecutionStrategy() {
            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext ec, ExecutionStrategyParameters p) throws NonNullableFieldWasNullException {
                null
            }
        }

        when:
        overridingStrategy.resolveFieldWithInfo(instrumentedExecutionContext, params)

        then:
        Object fetchedValue = instrumentation.fetchedValues.get("someField")
        fetchedValue != null
        fetchedValue.errors.size() == 1
        def exceptionWhileDataFetching = fetchedValue.errors[0] as ExceptionWhileDataFetching
        exceptionWhileDataFetching.getMessage().contains('This is the exception you are looking for')
        instrumentedExecutionContext.errors.size() == 1
        instrumentedExecutionContext.errors[0] == fetchedValue.errors[0]
    }

    def "#522 - single error during execution - not two errors"() {

        given:

        def dataFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                throw new RuntimeException("bang")
            }
        }

        def someFieldName = "someField"
        def testTypeName = "Test"

        def fieldDefinition = newFieldDefinition()
                .name(someFieldName)
                .type(nonNull(GraphQLString))
                .build()
        def objectType = newObject()
                .name(testTypeName)
                .field(fieldDefinition)
                .build()

        def someFieldCoordinates = FieldCoordinates.coordinates(testTypeName, someFieldName)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(someFieldCoordinates, dataFetcher)
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(objectType)
                .build()
        ExecutionContext executionContext = buildContext(schema)

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(objectType).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)
        Field field = new Field(someFieldName)

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(null)
                .nonNullFieldValidator(nullableFieldValidator)
                .field(mergedField(field))
                .fields(mergedSelectionSet(["someField": [mergedField(field)]]))
                .path(ResultPath.rootPath().segment("abc"))
                .build()

        when:
        FieldValueInfo fieldValueInfo = (executionStrategy.resolveFieldWithInfo(executionContext, parameters) as CompletableFuture).join()
        (fieldValueInfo.fieldValueObject as CompletableFuture).join()

        then:
        thrown(CompletionException)
        executionContext.errors.size() == 1 // only 1 error
        executionContext.errors[0] instanceof ExceptionWhileDataFetching
    }

    def "#163 completes value for an primitive type array"() {
        given:
        ExecutionContext executionContext = buildContext()
        long[] result = [1, 2, 3]
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().name("dummy").build())]]))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == [1, 2, 3]
    }

    def "#842 completes value for java.util.Stream"() {
        given:
        ExecutionContext executionContext = buildContext()
        Stream<Long> result = Stream.of(1L, 2L, 3L)
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().name("dummy").build())]]))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == [1, 2, 3]
    }

    def "#842 completes value for java.util.Iterator"() {
        given:
        ExecutionContext executionContext = buildContext()
        Iterator<Long> result = Arrays.asList(1L, 2L, 3L).iterator()
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().name("dummy").build())]]))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == [1, 2, 3]
    }


    def "#820 processes DataFetcherResult"() {
        given:

        ExecutionContext executionContext = buildContext()
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        def field = Field.newField("parent").sourceLocation(new SourceLocation(5, 10)).build()
        def parameters = newParameters()
                .path(ResultPath.fromList(["parent"]))
                .field(mergedField(field))
                .fields(mergedSelectionSet(["parent": [mergedField(field)]]))
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .executionStepInfo(executionStepInfo)
                .build()

        def executionData = ["child": [:]]
        when:
        def fetchedValue = executionStrategy.unboxPossibleDataFetcherResult(executionContext, parameters,
                DataFetcherResult.newResult().data(executionData)
                        .error(GraphqlErrorBuilder.newError().message("bad foo").path(["child", "foo"]).build())
                        .build()
        )

        then:
        fetchedValue.getFetchedValue() == executionData
//        executionContext.getErrors()[0].locations == [new SourceLocation(7, 20)]
        executionContext.getErrors()[0].message == "bad foo"
        executionContext.getErrors()[0].path == ["child", "foo"]
    }

    def "#1558 forward localContext on nonBoxed return from DataFetcher"() {
        given:
        def capturedLocalContext = "startingValue"
        executionStrategy = new ExecutionStrategy(dataFetcherExceptionHandler) {
            @Override
            CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
                return null
            }

            @Override
            protected FieldValueInfo completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
                // shows we set the local context if the value is non boxed
                capturedLocalContext = parameters.getLocalContext()
                return super.completeValue(executionContext, parameters)
            }
        }

        ExecutionContext executionContext = buildContext()
        def fieldType = StarWarsSchema.droidType
        def fldDef = StarWarsSchema.droidType.getFieldDefinition("name")
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        def field = Field.newField("name").sourceLocation(new SourceLocation(5, 10)).build()
        def localContext = "localContext"
        def parameters = newParameters()
                .path(ResultPath.fromList(["name"]))
                .localContext(localContext)
                .field(mergedField(field))
                .fields(mergedSelectionSet(["name": [mergedField(field)]]))
                .executionStepInfo(executionStepInfo)
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .build()

        when:
        executionStrategy.completeField(executionContext, parameters, new Object())

        then:
        capturedLocalContext == localContext
    }

    def "#820 processes DataFetcherResult just message"() {
        given:

        ExecutionContext executionContext = buildContext()
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        def field = Field.newField("parent").sourceLocation(new SourceLocation(5, 10)).build()
        def parameters = newParameters()
                .path(ResultPath.fromList(["parent"]))
                .field(mergedField(field))
                .fields(mergedSelectionSet(["parent": [mergedField(field)]]))
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .executionStepInfo(executionStepInfo)
                .build()

        def executionData = ["child": [:]]
        when:
        def fetchedValue = executionStrategy.unboxPossibleDataFetcherResult(executionContext, parameters,
                DataFetcherResult.newResult().data(executionData)
                        .error(GraphqlErrorBuilder.newError().message("bad foo").build())
                        .build())
        then:
        fetchedValue.getFetchedValue() == executionData
        executionContext.getErrors()[0].locations == []
        executionContext.getErrors()[0].message == "bad foo"
        executionContext.getErrors()[0].path == null
    }

    def "completes value for an iterable"() {
        given:
        ExecutionContext executionContext = buildContext()
        List<Long> result = [1L, 2L, 3L]
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).path(ResultPath.rootPath()).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

        def parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().name("dummy").build())]]))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == [1L, 2L, 3L]
    }

    def "when completeValue expects GraphQLList and non iterable or non array is passed then it should yield a TypeMismatch error"() {
        given:
        ExecutionContext executionContext = buildContext()
        Map<String, Object> result = new HashMap<>()
        def fieldType = list(Scalars.GraphQLInt)
        def fldDef = newFieldDefinition().name("test").type(fieldType).build()
        def typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(fieldType).fieldDefinition(fldDef).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext)

        def parameters = newParameters()
                .executionStepInfo(typeInfo)
                .source(result)
                .nonNullFieldValidator(nullableFieldValidator)
                .fields(mergedSelectionSet(["fld": [mergedField(Field.newField().name("dummy").build())]]))
                .field(mergedField(Field.newField().name("dummy").build()))
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters).fieldValueFuture.join()

        then:
        executionResult == null
        executionContext.errors.size() == 1
        executionContext.errors[0] instanceof TypeMismatchError
    }
}
