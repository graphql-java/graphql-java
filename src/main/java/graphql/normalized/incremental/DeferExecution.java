package graphql.normalized.incremental;

import graphql.ExperimentalApi;

import java.util.HashSet;
import java.util.Set;

/**
 * This class holds information about the defer execution of an ENF.
 * <p>
 * Given that an ENF can be linked with numerous defer labels, a {@link DeferExecution} instance comprises a
 * collection of these labels.
 * <p>
 * For example, this query:
 * <pre>
 *   query q {
 *    dog {
 *      ... @defer(label: "name-defer") {
 *        name
 *      }
 *      ... @defer(label: "another-name-defer") {
 *        name
 *      }
 *    }
 *  }
 *  </pre>
 *  Will result on a ENF linked to a {@link DeferExecution} with both labels: "name-defer" and "another-name-defer"
 */
@ExperimentalApi
public class DeferExecution {
    private final Set<DeferLabel> labels = new HashSet<>();

    public void addLabel(DeferLabel label) {
        this.labels.add(label);
    }

    public Set<DeferLabel> getLabels() {
        return this.labels;
    }
}
