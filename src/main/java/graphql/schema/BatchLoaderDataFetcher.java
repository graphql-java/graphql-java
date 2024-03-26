package graphql.schema;


import graphql.PublicSpi;

import java.util.List;

@PublicSpi
public interface BatchLoaderDataFetcher<T> extends DataFetcher<T> {

    List<String> getDataLoaderNames();

}
