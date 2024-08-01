package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.List;

/**
 * A result that is part of an execution that includes incrementally delivered data (data has been deferred of streamed).
 * <p>
 * For example, this query
 * <pre>
 * query {
 *   person(id: "cGVvcGxlOjE=") {
 *     ...HomeWorldFragment @defer(label: "homeWorldDefer")
 *     name
 *     films @stream(initialCount: 1, label: "filmsStream") {
 *       title
 *     }
 *   }
 * }
 * fragment HomeWorldFragment on Person {
 *   homeWorld {
 *     name
 *   }
 * }
 * </pre>
 * Could result on an incremental response with the following payloads (in JSON format here for simplicity).
 * <p>
 * <b>Response 1, the initial response does not contain any deferred or streamed results.</b>
 * <pre>
 * {
 *   "data": {
 *     "person": {
 *       "name": "Luke Skywalker",
 *       "films": [{ "title": "A New Hope" }]
 *     }
 *   },
 *   "hasNext": true
 * }
 * </pre>
 *
 * <b>Response 2, contains the defer payload and the first stream payload.</b>
 * <pre>
 * {
 *   "incremental": [
 *     {
 *       "label": "homeWorldDefer",
 *       "path": ["person"],
 *       "data": { "homeWorld": { "name": "Tatooine" } }
 *     },
 *     {
 *       "label": "filmsStream",
 *       "path": ["person", "films", 1],
 *       "items": [{ "title": "The Empire Strikes Back" }]
 *     }
 *   ],
 *   "hasNext": true
 * }
 * </pre>
 *
 * <b>Response 3, contains the final stream payload. Note how "hasNext" is "false", indicating this is the final response.</b>
 * <pre>
 * {
 *   "incremental": [
 *     {
 *       "label": "filmsStream",
 *       "path": ["person", "films", 2],
 *       "items": [{ "title": "Return of the Jedi" }]
 *     }
 *   ],
 *   "hasNext": false
 * }
 * </pre>
 *
 * <p>
 * This implementation is based on the state of <a href="https://github.com/graphql/graphql-spec/pull/742">Defer/Stream PR</a>
 * More specifically at the state of this
 * <a href="https://github.com/graphql/graphql-spec/commit/c630301560d9819d33255d3ba00f548e8abbcdc6">commit</a>
 * <p>
 * The execution behaviour should match what we get from running Apollo Server 4.9.5 with graphql-js v17.0.0-alpha.2
 */
@ExperimentalApi
public interface IncrementalExecutionResult extends ExecutionResult {
    /**
     * Indicates whether there are pending incremental data.
     *
     * @return "true" if there are incremental data, "false" otherwise.
     */
    boolean hasNext();

    /**
     * Returns a list of defer and/or stream payloads that the execution engine decided (for whatever reason) to resolve at the same time as the initial payload.
     * <p>
     * (...)this field may appear on both the initial and subsequent values.
     * <p>
     * <a href="https://github.com/graphql/graphql-spec/pull/742/files#diff-98d0cd153b72b63c417ad4238e8cc0d3385691ccbde7f7674bc0d2a718b896ecR271">source</a>
     *
     * @return a list of Stream and/or Defer payloads that were resolved at the same time as the initial payload.
     */
    @Nullable
    List<IncrementalPayload> getIncremental();

    /**
     * This method will return a {@link Publisher} of deferred results.  No field processing will be done
     * until a {@link org.reactivestreams.Subscriber} is attached to this publisher.
     * <p>
     * Once a {@link org.reactivestreams.Subscriber} is attached the deferred field result processing will be
     * started and published as a series of events.
     *
     * @return a {@link Publisher} that clients can subscribe to receive incremental payloads.
     */
    Publisher<DelayedIncrementalPartialResult> getIncrementalItemPublisher();
}
