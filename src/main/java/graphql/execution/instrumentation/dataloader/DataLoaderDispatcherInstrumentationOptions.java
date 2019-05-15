package graphql.execution.instrumentation.dataloader;

import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.function.BiFunction;

/**
 * The options that control the operation of {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
public class DataLoaderDispatcherInstrumentationOptions {

    private final boolean includeStatistics;

    private final BiFunction<Logger, DataLoaderRegistry, TrackingApproach> approachSupplier;

    private DataLoaderDispatcherInstrumentationOptions(boolean includeStatistics, BiFunction<Logger, DataLoaderRegistry, TrackingApproach> approachSupplier) {
        this.includeStatistics = includeStatistics;
        this.approachSupplier = approachSupplier;
    }

    public static DataLoaderDispatcherInstrumentationOptions newOptions() {
        return new DataLoaderDispatcherInstrumentationOptions(false, (log, registry) -> new FieldLevelTrackingApproach(registry));
    }

    public DataLoaderDispatcherInstrumentationOptions withTrackingApproach(BiFunction<Logger, DataLoaderRegistry, TrackingApproach> approachFactory) {
        return new DataLoaderDispatcherInstrumentationOptions(includeStatistics, approachFactory);
    }

    /**
     * This will toggle the ability to include java-dataloader statistics into the extensions
     * output of your query
     *
     * @param flag the switch to follow
     *
     * @return a new options object
     */
    public DataLoaderDispatcherInstrumentationOptions includeStatistics(boolean flag) {
        return new DataLoaderDispatcherInstrumentationOptions(flag, approachSupplier);
    }


    public boolean isIncludeStatistics() {
        return includeStatistics;
    }

    public TrackingApproach getApproach(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        return approachSupplier.apply(log, dataLoaderRegistry);
    }
}
