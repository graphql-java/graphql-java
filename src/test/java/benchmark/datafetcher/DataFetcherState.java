package benchmark.datafetcher;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;

@State(Scope.Thread)
public class DataFetcherState {
    public DataFetchingEnvironment environment = newDataFetchingEnvironment().source(new ResourceObject()).build();

    public PropertyDataFetcher publicPropertyFetcher = new PropertyDataFetcher("publicProperty");

    public PropertyDataFetcher privatePropertyFetcher = new PropertyDataFetcher("privateProperty");

    public PropertyDataFetcher publicFieldFetcher = new PropertyDataFetcher("publicField");

    public PropertyDataFetcher privateFieldValueFetcher = new PropertyDataFetcher("privateField");

    public PropertyDataFetcher unknownPropertyFetcher = new PropertyDataFetcher("unknownProperty");
}
