package graphql.execution.instrumentation.dataloader;

/**
 * The options that control the operation of {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
public class DataLoaderDispatcherInstrumentationOptions {

    private final boolean includeStatistics;
    private final boolean useCombinedApproach;

    private DataLoaderDispatcherInstrumentationOptions(boolean includeStatistics, boolean useCombinedApproach) {
        this.includeStatistics = includeStatistics;
        this.useCombinedApproach = useCombinedApproach;
    }

    public static DataLoaderDispatcherInstrumentationOptions newOptions() {
        return new DataLoaderDispatcherInstrumentationOptions(false, true);
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
        return new DataLoaderDispatcherInstrumentationOptions(flag, this.useCombinedApproach);
    }

    public DataLoaderDispatcherInstrumentationOptions useCombinedApproach(boolean flag) {
        return new DataLoaderDispatcherInstrumentationOptions(this.includeStatistics, flag);
    }

    public boolean isIncludeStatistics() {
        return includeStatistics;
    }

    public boolean isUseCombinedApproach() {
        return useCombinedApproach;
    }
}
