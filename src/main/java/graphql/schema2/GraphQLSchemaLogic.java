package graphql.schema2;

import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

import java.util.LinkedHashMap;
import java.util.Map;

public class GraphQLSchemaLogic {

    private final Map<String, DataFetcher> dataFetcherMap = new LinkedHashMap<>();
    private final Map<String, TypeResolver> typeResolverMap = new LinkedHashMap<>();
    private final Map<String, Object> metadata = new LinkedHashMap<>();
}
