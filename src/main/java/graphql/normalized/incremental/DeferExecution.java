package graphql.normalized.incremental;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLObjectType;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents details about the defer execution that can be associated with a {@link graphql.normalized.ExecutableNormalizedField}.
 *
 * Taking this schema as an example:
 * <pre>
 *     type Query { animal: Animal }
 *     interface Animal { name: String, age: Int }
 *     type Cat implements Animal { name: String, age: Int }
 *     type Dog implements Animal { name: String, age: Int }
 * </pre>
 *
 * <b>An ENF can be associated with multiple `DeferExecution`s</b>
 *
 * For example, this query:
 * <pre>
 *     query MyQuery {
 *         animal {
 *             ... @defer {
 *                 name
 *             }
 *             ... @defer {
 *                 name
 *             }
 *         }
 *     }
 * </pre>
 *
 * Would result in one ENF (name) associated with 2 `DeferExecution` instances. This is relevant for the execution
 * since the field would have to be included in 2 incremental payloads. (I know, there's some duplication here, but
 * this is the current state of the spec. There are some discussions happening around de-duplicating data in scenarios
 * like this, so this behaviour might change in the future).
 *
 * <b>A `DeferExecution` may be associated with a list of possible types</b>
 *
 * For example, this query:
 * <pre>
 *     query MyQuery {
 *         animal {
 *             ... @defer {
 *                 name
 *             }
 *         }
 *     }
 * </pre>
 * results in a `DeferExecution` with no label and possible types [Dog, Cat]
 *
 * <b>A `DeferExecution` may be associated with specific types</b>
 * For example, this query:
 * <pre>
 *     query MyQuery {
 *         animal {
 *             ... on Cat @defer {
 *                 name
 *             }
 *             ... on Dog {
 *                 name
 *             }
 *         }
 *     }
 * </pre>
 * results in a single ENF (name) associated with a `DeferExecution` with only "Cat" as a possible type. This means
 * that, at execution time, `name` should be deferred only if the return object is a "Cat" (but not a if it is a "Dog").
 *
 * <b>ENFs associated with the same instance of `DeferExecution` will be resolved in the same incremental response payload</b>
 * For example, take these queries:
 *
 * <pre>
 *     query Query1 {
 *         animal {
 *             ... @defer {
 *                 name
 *             }
 *             ... @defer {
 *                 age
 *             }
 *         }
 *     }
 *
 *     query Query2 {
 *         animal {
 *             ... @defer {
 *                 name
 *                 age
 *             }
 *         }
 *     }
 * </pre>
 *
 * In `Query1`, the ENFs name and age are associated with different instances of `DeferExecution`. This means that,
 * during execution, `name` and `age` can be delivered at different times (if name is resolved faster, it will be
 * delivered first, and vice-versa).
 * In `Query2` the fields will share the same instance of `DeferExecution`. This ensures that, at execution time, the
 * fields are guaranteed to be delivered together. In other words, execution should wait until the slowest field resolves
 * and deliver both fields at the same time.
 *
 */
@ExperimentalApi
public class DeferExecution {
    private final String label;
    private final Set<GraphQLObjectType> possibleTypes;

    public DeferExecution(@Nullable String label, Set<GraphQLObjectType> possibleTypes) {
        this.label = label;
        this.possibleTypes = possibleTypes;
    }

    /**
     * @return the label associated with this defer declaration
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * @return the concrete object types that are associated with this defer execution
     */
    public Set<GraphQLObjectType> getPossibleTypes() {
        return possibleTypes;
    }
}
