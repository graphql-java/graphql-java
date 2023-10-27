package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.language.AstPrinter
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.PropertyDataFetcher
import graphql.schema.StaticDataFetcher
import org.awaitility.Awaitility
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class InstrumentationTest extends Specification {


    def 'Instrumentation of simple serial execution'() {
        given:

        def query = """
        {
            hero {
                id
            }
        }
        """

        //
        // for testing purposes we must use AsyncExecutionStrategy under the covers to get such
        // serial behaviour.  The Instrumentation of a parallel strategy would be much different
        // and certainly harder to test

        def expected = [
                "start:execution",
                "onDispatched:execution",
                "start:parse",
                "onDispatched:parse",
                "end:parse",
                "start:validation",
                "onDispatched:validation",
                "end:validation",
                "start:execute-operation",
                "start:execution-strategy",
                "start:field-hero",
                "start:fetch-hero",
                "onDispatched:fetch-hero",
                "end:fetch-hero",
                "start:complete-hero",
                "start:execution-strategy",
                "start:field-id",
                "start:fetch-id",
                "onDispatched:fetch-id",
                "end:fetch-id",
                "start:complete-id",
                "onDispatched:complete-id",
                "end:complete-id",
                "onDispatched:field-id",
                "end:field-id",
                "onDispatched:execution-strategy",
                "end:execution-strategy",
                "onDispatched:complete-hero",
                "end:complete-hero",
                "onDispatched:field-hero",
                "end:field-hero",
                "onDispatched:execution-strategy",
                "end:execution-strategy",
                "onDispatched:execute-operation",
                "end:execute-operation",
                "end:execution",
        ]
        when:

        def instrumentation = new LegacyTestingInstrumentation()
        instrumentation.useOnDispatch = true

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new AsyncExecutionStrategy())
                .instrumentation(instrumentation)
                .build()

        graphQL.execute(query).data

        then:

        instrumentation.executionList == expected

        instrumentation.dfClasses.size() == 2
        instrumentation.dfClasses[0] == StaticDataFetcher.class
        instrumentation.dfClasses[1] == PropertyDataFetcher.class

        instrumentation.dfInvocations.size() == 2

        instrumentation.dfInvocations[0].getFieldDefinition().name == 'hero'
        instrumentation.dfInvocations[0].getExecutionStepInfo().getPath().toList() == ['hero']
        instrumentation.dfInvocations[0].getExecutionStepInfo().getUnwrappedNonNullType().name == 'Character'
        !instrumentation.dfInvocations[0].getExecutionStepInfo().isNonNullType()

        instrumentation.dfInvocations[1].getFieldDefinition().name == 'id'
        instrumentation.dfInvocations[1].getExecutionStepInfo().getPath().toList() == ['hero', 'id']
        instrumentation.dfInvocations[1].getExecutionStepInfo().getUnwrappedNonNullType().name == 'String'
        instrumentation.dfInvocations[1].getExecutionStepInfo().isNonNullType()
    }

    def "exceptions at field fetch will instrument exceptions correctly"() {

        given:

        def query = """
        {
            hero {
                id
            }
        }
        """

        def instrumentation = new LegacyTestingInstrumentation() {
            @Override
            DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
                return new DataFetcher<Object>() {
                    @Override
                    Object get(DataFetchingEnvironment environment) {
                        throw new RuntimeException("DF BANG!")
                    }
                }
            }
        }

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                .build()

        when:
        graphQL.execute(query)

        then:
        instrumentation.throwableList.size() == 1
        instrumentation.throwableList[0].getMessage() == "DF BANG!"
    }

    /**
     * This uses a stop and go pattern and multiple threads.  Each time
     * the execution strategy is invoked, the data fetchers are held
     * and when all the fields are dispatched, the signal is released
     *
     * Clearly you would not do this in production but this how say
     * java-dataloader works.  That is calls inside DataFetchers are "batched"
     * until a "dispatch" signal is made.
     */
    class WaitingInstrumentation extends SimplePerformantInstrumentation {

        final AtomicBoolean goSignal = new AtomicBoolean()

        @Override
        ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
            System.out.println(String.format("t%s setting go signal off", Thread.currentThread().getId()))
            goSignal.set(false)
            return new ExecutionStrategyInstrumentationContext() {

                @Override
                void onDispatched(CompletableFuture<ExecutionResult> result) {
                    System.out.println(String.format("t%s setting go signal on", Thread.currentThread().getId()))
                    goSignal.set(true)
                }

                @Override
                void onCompleted(ExecutionResult result, Throwable t) {
                }
            }
        }

        @NotNull
        @Override
        DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
            System.out.println(String.format("t%s instrument DF for %s", Thread.currentThread().getId(), parameters.environment.getExecutionStepInfo().getPath()))

            return new DataFetcher<Object>() {
                @Override
                Object get(DataFetchingEnvironment environment) {
                    // off thread call - that waits
                    return CompletableFuture.supplyAsync({
                        def value = dataFetcher.get(environment)
                        System.out.println(String.format("   t%s awaiting %s", Thread.currentThread().getId(), environment.getExecutionStepInfo().getPath()))
                        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilTrue(goSignal)
                        System.out.println(String.format("      t%s returning value %s", Thread.currentThread().getId(), environment.getExecutionStepInfo().getPath()))
                        return value
                    })
                }
            }
        }
    }


    def "beginExecutionStrategy will be called for each invocation"() {

        given:

        def query = """
        {
            artoo: hero {
                id
            }
            
            r2d2 : hero {
               name
            }
        }
        """

        when:

        WaitingInstrumentation instrumentation = new WaitingInstrumentation()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                .build()

        def er = graphQL.execute(query)

        then:

        er.data == [artoo: [id: '2001'], r2d2: [name: 'R2-D2']]
    }

    def "document and variables can be intercepted by instrumentation and changed"() {

        given:

        def query = '''query Q($var: String!) {
  human(id: $var) {
    id
  }
}
'''
        def newQuery = '''query Q($var: String!) {
  human(id: $var) {
    id
    name
  }
}
'''

        def instrumentation = new ModernTestingInstrumentation() {

            @Override
            DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                this.capturedData["originalDoc"] = AstPrinter.printAst(documentAndVariables.getDocument())
                this.capturedData["originalVariables"] = documentAndVariables.getVariables()
                def newDoc = new Parser().parseDocument(newQuery)
                def newVars = [var: "1001"]
                documentAndVariables.transform({ builder -> builder.document(newDoc).variables(newVars) })
            }
        }

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                .build()

        when:
        def variables = [var: "1000"]
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables)) // Luke

        then:
        er.data == [human: [id: "1001", name: 'Darth Vader']]
        instrumentation.capturedData["originalDoc"] == query
        instrumentation.capturedData["originalVariables"] == variables
    }

    def "an instrumentation can return null and graphql calling code can handle it when inside a chain"() {
        given:

        def query = """
        {
            hero {
                id
            }
        }
        """

        //
        // for testing purposes we must use AsyncExecutionStrategy under the covers to get such
        // serial behaviour.  The Instrumentation of a parallel strategy would be much different
        // and certainly harder to test

        def expected = [
                "start:execution",
                // because a null context was returned, there is no onDispatched and end
                //"onDispatched:execution",
                "start:parse",
                "onDispatched:parse",
                "end:parse",
                "start:validation",
                "onDispatched:validation",
                "end:validation",
                "start:execute-operation",
                "start:execution-strategy",
                "start:field-hero",
                "start:fetch-hero",
                "onDispatched:fetch-hero",
                "end:fetch-hero",
                "start:complete-hero",
                "start:execution-strategy",
                "start:field-id",
                "start:fetch-id",
                "onDispatched:fetch-id",
                "end:fetch-id",
                "start:complete-id",
                "onDispatched:complete-id",
                "end:complete-id",
                "onDispatched:field-id",
                "end:field-id",
                "onDispatched:execution-strategy",
                "end:execution-strategy",
                "onDispatched:complete-hero",
                "end:complete-hero",
                "onDispatched:field-hero",
                "end:field-hero",
                "onDispatched:execution-strategy",
                "end:execution-strategy",
                "onDispatched:execute-operation",
                "end:execute-operation",
                //"end:execution",
        ]
        when:

        def instrumentation = new ModernTestingInstrumentation() {
            @Override
            InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
                executionList.add("start:execution")
                return null
            }
        }
        instrumentation.useOnDispatch = true

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new AsyncExecutionStrategy())
                .instrumentation(instrumentation)
                .build()

        graphQL.execute(query)

        then:
        instrumentation.executionList == expected
    }

    def "an instrumentation can return null and graphql calling code can handle it when not inside a chain"() {

        given:

        def query = '''query Q($var: String!) {
                                  human(id: $var) {
                                    id
                                    name
                                  }
                                }
                            '''

        def instrumentation = new AllNullTestingInstrumentation()

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                .doNotAddDefaultInstrumentations() // important, otherwise a chained one wil be used
                .build()

        when:
        def variables = [var: "1001"]
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables)) // Luke

        then:
        er.data == [human: [id: "1001", name: 'Darth Vader']]

        def expected = [
                "start:execution",
                "start:parse",
                "start:validation",
                "start:execute-operation",
                "start:execution-strategy",
                "start:field-human",
                "start:fetch-human",
                "start:complete-human",
                "start:execution-strategy",
                "start:field-id",
                "start:fetch-id",
                "start:complete-id",
                "start:field-name",
                "start:fetch-name",
                "start:complete-name",
        ]

        instrumentation.executionList == expected
    }

    class StringInstrumentationState implements InstrumentationState {
        StringInstrumentationState(String value) {
            this.value = value
        }

        String value
    }

    def "can have an single async createState() in play"() {


        given:

        def query = '''query Q($var: String!) {
                                  human(id: $var) {
                                    id
                                    name
                                  }
                                }
                            '''


        def instrumentation1 = new SimplePerformantInstrumentation() {
            @Override
            CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
                return CompletableFuture.supplyAsync {
                    return new StringInstrumentationState("I1")
                } as CompletableFuture<InstrumentationState>
            }

            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                return CompletableFuture.completedFuture(
                        executionResult.transform { it.addExtension("i1", ((StringInstrumentationState) state).value) }
                )
            }
        }

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation1)
                .doNotAddDefaultInstrumentations() // important, otherwise a chained one wil be used
                .build()

        when:
        def variables = [var: "1001"]
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables)) // Luke

        then:
        er.extensions == [i1: "I1"]
    }
}
