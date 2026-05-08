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
}
