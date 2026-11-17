package nullability;

import graphql.TypeResolutionEnvironment;
import graphql.execution.ValueUnboxer;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.TypeResolver;
import graphql.schema.idl.EnumValuesProvider;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class UnsafeSpiUsage {
    public static void nullableResults(DataFetcher<String> fetcher, AsyncDataFetcher<String> async,
                                       DataFetchingEnvironment environment, TypeResolver resolver,
                                       TypeResolutionEnvironment typeEnvironment, DataFetcherFactory<String> factory,
                                       GraphQLFieldDefinition field, EnumValuesProvider enums) throws Exception {
        fetcher.get(environment).length();
        java.util.concurrent.CompletableFuture<String> nonNullValues = async.get(environment);
        resolver.getType(typeEnvironment).getName();
        ValueUnboxer.DEFAULT.unbox(null).toString();
        factory.get(field).getClass();
        enums.getValue("MISSING").toString();
    }
}
