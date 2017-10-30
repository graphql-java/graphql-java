package graphql.execution.instrumentation;

/**
 * An {@link graphql.execution.instrumentation.Instrumentation} implementation can create this as a stateful object that is then passed
 * to each instrumentation method, allowing state to be passed down with the request execution
 *
 * @see graphql.execution.instrumentation.Instrumentation#createPreExecutionState(graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters) ()
 */
public interface InstrumentationPreExecutionState {
}