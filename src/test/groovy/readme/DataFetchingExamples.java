package readme;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings({"Convert2Lambda", "unused"})
public class DataFetchingExamples {

    static class DatabaseSecurityCtx {

    }

    static class ID {

    }

    //::FigureA
    class ProductDTO {

        private ID id;
        private String name;
        private String description;
        private Double cost;
        private Double tax;
        private LocalDateTime launchDate;

        // ...

        public String getName() {
            return name;
        }

        // ...

        public String getLaunchDate(DataFetchingEnvironment environment) {
            String dateFormat = environment.getArgument("dateFormat");
            return yodaTimeFormatter(launchDate,dateFormat);
        }
    }
    //::/FigureA

    void getProductsDataFetcher() {
        //::FigureB
        DataFetcher productsDataFetcher = new DataFetcher<List<ProductDTO>>() {
            @Override
            public List<ProductDTO> get(DataFetchingEnvironment environment) {
                DatabaseSecurityCtx ctx = environment.getContext();

                List<ProductDTO> products;
                String match = environment.getArgument("match");
                if (match != null) {
                    products = fetchProductsFromDatabaseWithMatching(ctx, match);
                } else {
                    products = fetchAllProductsFromDatabase(ctx);
                }
                return products;
            }
        };
        //::/FigureB
    }

    private String yodaTimeFormatter(LocalDateTime date, String dateFormat) {
        return null;
    }

    private List<ProductDTO> fetchProductsFromDatabaseWithMatching(DatabaseSecurityCtx ctx, String match) {
        return null;
    }

    private List<ProductDTO> fetchAllProductsFromDatabase(DatabaseSecurityCtx ctx) {
        return null;
    }

}
