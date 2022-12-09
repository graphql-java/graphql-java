package graphql.extensions;

import com.google.common.collect.ImmutableMap;
import graphql.ExecutionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static graphql.Assert.assertNotNull;

public class ExtensionsBuilder {

    // thread safe since there can be many changes say in DFs across threads
    private final List<Map<?, Object>> changes = new CopyOnWriteArrayList<>();
    private final ExtensionsMerger extensionsMerger;


    private ExtensionsBuilder(ExtensionsMerger extensionsMerger) {
        this.extensionsMerger = extensionsMerger;
    }

    public static ExtensionsBuilder newExtensionsBuilder() {
        return new ExtensionsBuilder(ExtensionsMerger.DEFAULT);
    }

    public static ExtensionsBuilder newExtensionsBuilder(ExtensionsMerger extensionsMerger) {
        return new ExtensionsBuilder(extensionsMerger);
    }

    public ExtensionsBuilder addValues(Map<Object, Object> newValues) {
        assertNotNull(newValues);
        changes.add(newValues);
        return this;
    }

    public ExtensionsBuilder addValue(Object key, Object value) {
        assertNotNull(key);
        return addValues(Collections.singletonMap(key, value));
    }

    public Map<Object, Object> buildExtensions() {
        if (changes.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<Object, Object> outMap = new LinkedHashMap<>(changes.get(0));
        for (int i = 1; i < changes.size(); i++) {
            outMap = extensionsMerger.merge(outMap, changes.get(i));
            assertNotNull(outMap, () -> "You MUST provide a non null Map from ExtensionsMerger.merge()");
        }
        return outMap;
    }

    public ExecutionResult setExtensions(ExecutionResult executionResult) {
        assertNotNull(executionResult);
        return executionResult.transform(builder -> builder.extensions(buildExtensions()));
    }
}
