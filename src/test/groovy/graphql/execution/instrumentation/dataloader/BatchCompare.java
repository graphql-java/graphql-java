package graphql.execution.instrumentation.dataloader;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static java.nio.charset.Charset.defaultCharset;

public class BatchCompare {

    GraphQLSchema buildDataLoaderSchema(BatchCompareDataFetchers batchCompareDataFetchers) {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("storesanddepartments.graphqls");
        Reader streamReader = new InputStreamReader(resourceAsStream, defaultCharset());
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(streamReader);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Query")
                        .dataFetcher("shops", batchCompareDataFetchers.shopsDataFetcher)
                        .dataFetcher("expensiveShops", batchCompareDataFetchers.expensiveShopsDataFetcher)
                )
                .type(TypeRuntimeWiring.newTypeWiring("Shop")
                        .dataFetcher("departments", batchCompareDataFetchers.departmentsForShopDataLoaderDataFetcher)
                        .dataFetcher("expensiveDepartments", batchCompareDataFetchers.departmentsForShopDataLoaderDataFetcher)
                )
                .type(TypeRuntimeWiring.newTypeWiring("Department")
                        .dataFetcher("products", batchCompareDataFetchers.productsForDepartmentDataLoaderDataFetcher)
                        .dataFetcher("expensiveProducts", batchCompareDataFetchers.productsForDepartmentDataLoaderDataFetcher)
                )
                .build();

        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }
}

