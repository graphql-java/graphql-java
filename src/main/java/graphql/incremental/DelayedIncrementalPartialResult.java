package graphql.incremental;

import graphql.ExperimentalApi;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents a result that is delivered asynchronously, after the initial {@link IncrementalExecutionResult}.
 * <p>
 * Multiple defer and/or stream payloads (represented by {@link IncrementalPayload}) can be part of the same
 * {@link DelayedIncrementalPartialResult}
 */
@ExperimentalApi
public interface DelayedIncrementalPartialResult {
    /**
     * @return a list of defer and/or stream payloads.
     */
    @Nullable
    List<IncrementalPayload> getIncremental();

    /**
     * Indicates whether the stream will continue emitting {@link DelayedIncrementalPartialResult}s after this one.
     * <p>
     * The value returned by this method should be "true" for all but the last response in the stream. The value of this
     * entry is `false` for the last response of the stream.
     *
     * @return "true" if there are more responses in the stream, "false" otherwise.
     */
    boolean hasNext();

    /**
     * @return a map of extensions or null if there are none
     */
    @Nullable
    Map<Object, Object> getExtensions();

    /**
     * @return a map of the result that strictly follows the spec
     */
    Map<String, Object> toSpecification();
}
