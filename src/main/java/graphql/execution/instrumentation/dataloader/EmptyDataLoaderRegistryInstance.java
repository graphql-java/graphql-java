package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.function.Function;

public class EmptyDataLoaderRegistryInstance {
    public static final DataLoaderRegistry EMPTY_DATALOADER_REGISTRY = new DataLoaderRegistry() {
        //
        private static final String ERROR_MESSAGE = "You MUST set in your own DataLoaderRegistry to use data loader";

        @Override
        public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }

        @Override
        public <K, V> DataLoader<K, V> computeIfAbsent(final String key,
                                                       final Function<String, DataLoader<?, ?>> mappingFunction) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }

        @Override
        public DataLoaderRegistry unregister(String key) {
            return Assert.assertShouldNeverHappen(ERROR_MESSAGE);
        }
    };
}
