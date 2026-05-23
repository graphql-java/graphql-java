package graphql.execution.instrumentation

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import spock.lang.Specification

class InstrumentationDefaultMethodsTest extends Specification {

    def "default begin field fetching does not allocate an adapter for inherited no-op"() {
        given:
        def instrumentation = new Instrumentation() {}

        when:
        def context = instrumentation.beginFieldFetching(null, null)

        then:
        context.is(FieldFetchingInstrumentationContext.NOOP)
    }

    def "simple performant instrumentation begin field fetching does not allocate an adapter for inherited no-op"() {
        when:
        def context = SimplePerformantInstrumentation.INSTANCE.beginFieldFetching(null, null)

        then:
        context.is(FieldFetchingInstrumentationContext.NOOP)
    }

    def "default begin field fetching does not allocate an adapter when deprecated override returns no-op"() {
        given:
        def instrumentation = new Instrumentation() {
            @Override
            InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                return SimpleInstrumentationContext.noOp()
            }
        }

        when:
        def context = instrumentation.beginFieldFetching(null, null)

        then:
        context.is(FieldFetchingInstrumentationContext.NOOP)
    }

    def "default begin field fetching still adapts deprecated begin field fetch overrides"() {
        given:
        def events = []
        def instrumentation = new Instrumentation() {
            @Override
            InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                return new InstrumentationContext<Object>() {
                    @Override
                    void onDispatched() {
                        events.add("dispatched")
                    }

                    @Override
                    void onCompleted(Object result, Throwable t) {
                        events.add(result)
                    }
                }
            }
        }

        when:
        def context = instrumentation.beginFieldFetching(null, null)
        context.onDispatched()
        context.onFetchedValue("ignored")
        context.onCompleted("completed", null)

        then:
        events == ["dispatched", "completed"]
    }

    def "chained field fetching does not allocate a chained context when all entries are no-op"() {
        given:
        def instrumentation = new ChainedInstrumentation(new Instrumentation() {}, SimplePerformantInstrumentation.INSTANCE)
        def state = instrumentation.createStateAsync(null).join()

        when:
        def context = instrumentation.beginFieldFetching(null, state)

        then:
        context.is(FieldFetchingInstrumentationContext.NOOP)
    }

    def "chained field fetching returns the only real context directly"() {
        given:
        def realContext = Mock(FieldFetchingInstrumentationContext)
        def instrumentation = new ChainedInstrumentation(
                new Instrumentation() {},
                instrumentationReturning(realContext),
                SimplePerformantInstrumentation.INSTANCE
        )
        def state = instrumentation.createStateAsync(null).join()

        when:
        def context = instrumentation.beginFieldFetching(null, state)

        then:
        context.is(realContext)
    }

    def "chained field fetching treats null contexts as no-op entries"() {
        given:
        def instrumentation = new ChainedInstrumentation(new Instrumentation() {}, instrumentationReturning(null))
        def state = instrumentation.createStateAsync(null).join()

        when:
        def context = instrumentation.beginFieldFetching(null, state)

        then:
        context.is(FieldFetchingInstrumentationContext.NOOP)
    }

    def "chained field fetching keeps multiple real contexts and skips canonical no-ops"() {
        given:
        def events = []
        def instrumentation = new ChainedInstrumentation(
                new Instrumentation() {},
                instrumentationReturning(recordingContext("first", events)),
                SimplePerformantInstrumentation.INSTANCE,
                instrumentationReturning(recordingContext("second", events))
        )
        def state = instrumentation.createStateAsync(null).join()

        when:
        def context = instrumentation.beginFieldFetching(null, state)
        context.onDispatched()
        context.onFetchedValue("value")
        context.onCompleted("complete", null)

        then:
        events == [
                "first-dispatched",
                "second-dispatched",
                "first-fetched-value",
                "second-fetched-value",
                "first-completed-complete",
                "second-completed-complete",
        ]
    }

    private static Instrumentation instrumentationReturning(FieldFetchingInstrumentationContext context) {
        return new Instrumentation() {
            @Override
            FieldFetchingInstrumentationContext beginFieldFetching(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                return context
            }
        }
    }

    private static FieldFetchingInstrumentationContext recordingContext(String name, List<String> events) {
        return new FieldFetchingInstrumentationContext() {
            @Override
            void onDispatched() {
                events.add(name + "-dispatched")
            }

            @Override
            void onFetchedValue(Object fetchedValue) {
                events.add(name + "-fetched-" + fetchedValue)
            }

            @Override
            void onCompleted(Object result, Throwable t) {
                events.add(name + "-completed-" + result)
            }
        }
    }
}
