package graphql.extensions;

import com.google.common.collect.ImmutableMap;
import graphql.ExecutionResult;
import graphql.PublicApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static graphql.Assert.assertNotNull;

/**
 * This class can be used to help build the graphql `extensions` map.  A series of changes to the extensions can
 * be added and these will be merged together via a {@link ExtensionsMerger} implementation and that resultant
 * map can be used as the `extensions`
 * <p>
 * The engine will place a {@link ExtensionsBuilder} into the {@link graphql.GraphQLContext} (if one is not manually placed there)
 * and hence {@link graphql.schema.DataFetcher}s can use it to build up extensions progressively.
 * <p>
 * At the end of the execution, the {@link ExtensionsBuilder} will be used to build a graphql `extensions` map that
 * is placed in the {@link ExecutionResult}
 */
@PublicApi
public class ExtensionsBuilder {

    // thread safe since there can be many changes say in DFs across threads
    private final List<Map<Object, Object>> changes = new CopyOnWriteArrayList<>();
    private final ExtensionsMerger extensionsMerger;


    private ExtensionsBuilder(ExtensionsMerger extensionsMerger) {
        this.extensionsMerger = extensionsMerger;
    }

    /**
     * @return a new ExtensionsBuilder using a default merger
     */
    public static ExtensionsBuilder newExtensionsBuilder() {
        return new ExtensionsBuilder(ExtensionsMerger.DEFAULT);
    }

    /**
     * This creates a new ExtensionsBuilder with the provided {@link ExtensionsMerger}
     *
     * @param extensionsMerger the merging code to use
     *
     * @return a new ExtensionsBuilder using the provided merger
     */
    public static ExtensionsBuilder newExtensionsBuilder(ExtensionsMerger extensionsMerger) {
        return new ExtensionsBuilder(extensionsMerger);
    }

    /**
     * @return how many extension changes have been made so far
     */
    public int getChangeCount() {
        return changes.size();
    }

    /**
     * Adds new values into the extension builder
     *
     * @param newValues the new values to add
     *
     * @return this builder for fluent style reasons
     */
    public ExtensionsBuilder addValues(@NotNull Map<Object, Object> newValues) {
        assertNotNull(newValues);
        if (!newValues.isEmpty()) {
            changes.add(newValues);
        }
        return this;
    }

    /**
     * Adds a single new value into the extension builder
     *
     * @param key   the key in the extensions
     * @param value the value in the extensions
     *
     * @return this builder for fluent style reasons
     */
    public ExtensionsBuilder addValue(@NotNull Object key, @Nullable Object value) {
        assertNotNull(key);
        return addValues(Collections.singletonMap(key, value));
    }

    /**
     * This builds an extensions map from this builder, merging together the values provided
     *
     * @return a new extensions map
     */
    public Map<Object, Object> buildExtensions() {
        if (changes.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<Object, Object> firstChange = changes.get(0);
        if (changes.size() == 1) {
            return firstChange;
        }
        Map<Object, Object> outMap = new LinkedHashMap<>(firstChange);
        for (int i = 1; i < changes.size(); i++) {
            Map<Object, Object> newMap = extensionsMerger.merge(outMap, changes.get(i));
            assertNotNull(outMap, () -> "You MUST provide a non null Map from ExtensionsMerger.merge()");
            outMap = newMap;
        }
        return outMap;
    }

    /**
     * This sets new  extensions into the provided {@link ExecutionResult}, overwriting any previous values
     *
     * @param executionResult the result to set these extensions into
     *
     * @return a new ExecutionResult with the extensions values in this builder
     */
    public ExecutionResult setExtensions(ExecutionResult executionResult) {
        assertNotNull(executionResult);
        Map<Object, Object> currentExtensions = executionResult.getExtensions();
        Map<Object, Object> builderExtensions = buildExtensions();
        // if there was no extensions map before, and we are not adding anything new
        // then leave it null
        if (currentExtensions == null && builderExtensions.isEmpty()) {
            return executionResult;
        }
        return executionResult.transform(builder -> builder.extensions(builderExtensions));
    }
}
