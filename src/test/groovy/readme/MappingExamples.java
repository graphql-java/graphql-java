package readme;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.PropertyDataFetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"Convert2Lambda", "unused", "ClassCanBeStatic"})
public class MappingExamples {

    interface ProductInfo {
        String getId();

        String getName();

        String getDescription();
    }

    interface ProductCostInfo {
        float getCost();
    }

    interface ProductTaxInfo {
        float getTax();
    }


    void productsDataFetcher() {

        DataFetcher productsDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment env) {
                String matchArg = env.getArgument("match");

                List<ProductInfo> productInfo = getMatchingProducts(matchArg);

                List<ProductCostInfo> productCostInfo = getProductCosts(productInfo);

                List<ProductTaxInfo> productTaxInfo = getProductTax(productInfo);

                return mapDataTogether(productInfo, productCostInfo, productTaxInfo);
            }
        };
    }

    private Object mapDataTogether(List<ProductInfo> productInfo, List<ProductCostInfo> productCostInfo, List<ProductTaxInfo> productTaxInfo) {
        return null;
    }

    private List<Map> mapDataTogetherViaMap(List<ProductInfo> productInfo, List<ProductCostInfo> productCostInfo, List<ProductTaxInfo> productTaxInfo) {
        List<Map> unifiedView = new ArrayList<>();
        for (int i = 0; i < productInfo.size(); i++) {
            ProductInfo info = productInfo.get(i);
            ProductCostInfo cost = productCostInfo.get(i);
            ProductTaxInfo tax = productTaxInfo.get(i);

            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put("id", info.getId());
            objectMap.put("name", info.getName());
            objectMap.put("description", info.getDescription());
            objectMap.put("cost", cost.getCost());
            objectMap.put("tax", tax.getTax());

            unifiedView.add(objectMap);
        }
        return unifiedView;
    }

    class ProductDTO {
        private final String id;
        private final String name;
        private final String description;
        private final Float cost;
        private final Float tax;

        public ProductDTO(String id, String name, String description, Float cost, Float tax) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.tax = tax;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Float getCost() {
            return cost;
        }

        public Float getTax() {
            return tax;
        }
    }

    private List<ProductDTO> mapDataTogetherViaDTO(List<ProductInfo> productInfo, List<ProductCostInfo> productCostInfo, List<ProductTaxInfo> productTaxInfo) {
        List<ProductDTO> unifiedView = new ArrayList<>();
        for (int i = 0; i < productInfo.size(); i++) {
            ProductInfo info = productInfo.get(i);
            ProductCostInfo cost = productCostInfo.get(i);
            ProductTaxInfo tax = productTaxInfo.get(i);

            ProductDTO productDTO = new ProductDTO(
                    info.getId(),
                    info.getName(),
                    info.getDescription(),
                    cost.getCost(),
                    tax.getTax()
            );
            unifiedView.add(productDTO);
        }
        return unifiedView;
    }

    private void directWiring() {

        GraphQLFieldDefinition descriptionField = GraphQLFieldDefinition.newFieldDefinition()
                .name("description")
                .type(Scalars.GraphQLString)
                .dataFetcher(PropertyDataFetcher.fetching("desc"))
                .build();

    }

    private List<ProductInfo> getMatchingProducts(String matchArg) {
        return null;
    }

    private List<ProductCostInfo> getProductCosts(List<ProductInfo> productInfo) {
        return null;
    }

    private List<ProductTaxInfo> getProductTax(List<ProductInfo> productInfo) {
        return null;
    }
}
