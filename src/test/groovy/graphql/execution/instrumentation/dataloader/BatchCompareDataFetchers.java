package graphql.execution.instrumentation.dataloader;

import graphql.execution.batched.Batched;
import graphql.execution.instrumentation.dataloader.models.Department;
import graphql.execution.instrumentation.dataloader.models.Product;
import graphql.execution.instrumentation.dataloader.models.Shop;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class BatchCompareDataFetchers {

    static AtomicLong departmentsForShopsBatchLoaderCounter = new AtomicLong();

    static AtomicLong productsForDepartmentsBatchLoaderCounter = new AtomicLong();

    static AtomicBoolean useAsyncBatchLoading = new AtomicBoolean(false);

    public static void useAsyncBatchLoading(boolean flag) {
        useAsyncBatchLoading.set(flag);
    }

    static void resetState() {
        departmentsForShopsBatchLoaderCounter.set(0);
        productsForDepartmentsBatchLoaderCounter.set(0);
        departmentsForShopDataLoader.clearAll();
        productsForDepartmentDataLoader.clearAll();
        useAsyncBatchLoading.set(false);
    }

    // Shops
    private static final Map<String, Shop> shops = new LinkedHashMap<>();
    private static final Map<String, Shop> expensiveShops = new LinkedHashMap<>();

    static {
        shops.put("shop-1", new Shop("shop-1", "Shop 1", Arrays.asList("department-1", "department-2", "department-3")));
        shops.put("shop-2", new Shop("shop-2", "Shop 2", Arrays.asList("department-4", "department-5", "department-6")));
        shops.put("shop-3", new Shop("shop-3", "Shop 3", Arrays.asList("department-7", "department-8", "department-9")));

        expensiveShops.put("exshop-1", new Shop("exshop-1", "ExShop 1", Arrays.asList("department-1", "department-2", "department-3")));
        expensiveShops.put("exshop-2", new Shop("exshop-2", "ExShop 2", Arrays.asList("department-4", "department-5", "department-6")));
        expensiveShops.put("exshop-3", new Shop("exshop-3", "ExShop 3", Arrays.asList("department-7", "department-8", "department-9")));
    }


    public static DataFetcher<CompletableFuture<List<Shop>>> shopsDataFetcher =
            environment -> supplyAsyncWithSleep(() -> new ArrayList<>(shops.values()));

    public static DataFetcher<CompletableFuture<List<Shop>>> expensiveShopsDataFetcher = environment ->
            supplyAsyncWithSleep(() -> new ArrayList<>(expensiveShops.values()));

    // Departments
    private static Map<String, Department> departments = new LinkedHashMap<>();

    static {
        departments.put("department-1", new Department("department-1", "Department 1", singletonList("product-1")));
        departments.put("department-2", new Department("department-2", "Department 2", singletonList("product-2")));
        departments.put("department-3", new Department("department-3", "Department 3", singletonList("product-3")));
        departments.put("department-4", new Department("department-4", "Department 4", singletonList("product-4")));
        departments.put("department-5", new Department("department-5", "Department 5", singletonList("product-5")));
        departments.put("department-6", new Department("department-6", "Department 6", singletonList("product-6")));
        departments.put("department-7", new Department("department-7", "Department 7", singletonList("product-7")));
        departments.put("department-8", new Department("department-8", "Department 8", singletonList("product-8")));
        departments.put("department-9", new Department("department-9", "Department 9", singletonList("product-9")));
    }

    private static List<Department> getDepartmentsForShop(Shop shop) {
        return shop.getDepartmentIds().stream().map(id -> departments.get(id)).collect(Collectors.toList());
    }

    private static List<List<Department>> getDepartmentsForShops(List<Shop> shops) {
        System.out.println("getDepartmentsForShops batch: " + shops);
        List<List<Department>> departmentsResult = shops.stream().map(BatchCompareDataFetchers::getDepartmentsForShop).collect(Collectors.toList());
        System.out.println("result " + departmentsResult);
        return departmentsResult;
    }

    public static DataFetcher<List<List<Department>>> departmentsForShopsBatchedDataFetcher = new DataFetcher<List<List<Department>>>() {
        @Override
        @Batched
        public List<List<Department>> get(DataFetchingEnvironment environment) {
            List<Shop> shops = environment.getSource();
            return getDepartmentsForShops(shops);
        }
    };

    private static BatchLoader<String, List<Department>> departmentsForShopsBatchLoader = ids -> maybeAsyncWithSleep(() -> {
        System.out.println("ids" + ids);
        departmentsForShopsBatchLoaderCounter.incrementAndGet();
        List<Shop> shopList = new ArrayList<>();
        for (String id : ids) {
            Optional<Shop> shop1 = Optional.ofNullable(shops.get(id));
            Optional<Shop> shop2 = Optional.ofNullable(expensiveShops.get(id));
            if (shop1.isPresent()) {
                shopList.add(shop1.get());
            } else {
                shopList.add(shop2.get());
            }
        }
        return completedFuture(getDepartmentsForShops(shopList));
    });

    public static DataLoader<String, List<Department>> departmentsForShopDataLoader = new DataLoader<>(departmentsForShopsBatchLoader);

    public static DataFetcher<CompletableFuture<List<Department>>> departmentsForShopDataLoaderDataFetcher = environment -> {
        Shop shop = environment.getSource();
        return departmentsForShopDataLoader.load(shop.getId());
    };

    // Products
    private static Map<String, Product> products = new LinkedHashMap<>();

    static {
        products.put("product-1", new Product("product-1", "Product 1", 0));
        products.put("product-2", new Product("product-2", "Product 2", 0));
        products.put("product-3", new Product("product-3", "Product 3", 0));
        products.put("product-4", new Product("product-4", "Product 4", 0));
        products.put("product-5", new Product("product-5", "Product 5", 0));
        products.put("product-6", new Product("product-6", "Product 6", 0));
        products.put("product-7", new Product("product-7", "Product 7", 0));
        products.put("product-8", new Product("product-8", "Product 8", 0));
        products.put("product-9", new Product("product-9", "Product 9", 0));
    }

    private static List<Product> getProductsForDepartment(Department department) {
        return department.getProductIds().stream().map(id -> products.get(id)).collect(Collectors.toList());
    }

    private static List<List<Product>> getProductsForDepartments(List<Department> departments) {
        System.out.println("getProductsForDepartments batch: " + departments);
        return departments.stream().map(BatchCompareDataFetchers::getProductsForDepartment).collect(Collectors.toList());
    }

    public static DataFetcher<List<List<Product>>> productsForDepartmentsBatchedDataFetcher = new DataFetcher<List<List<Product>>>() {
        @Override
        @Batched
        public List<List<Product>> get(DataFetchingEnvironment environment) {
            List<Department> departments = environment.getSource();
            return getProductsForDepartments(departments);
        }
    };

    private static BatchLoader<String, List<Product>> productsForDepartmentsBatchLoader = ids -> maybeAsyncWithSleep(() -> {
        productsForDepartmentsBatchLoaderCounter.incrementAndGet();
        List<Department> d = ids.stream().map(departments::get).collect(Collectors.toList());
        return completedFuture(getProductsForDepartments(d));
    });

    public static DataLoader<String, List<Product>> productsForDepartmentDataLoader = new DataLoader<>(productsForDepartmentsBatchLoader);

    public static DataFetcher<CompletableFuture<List<Product>>> productsForDepartmentDataLoaderDataFetcher = environment -> {
        Department department = environment.getSource();
        return productsForDepartmentDataLoader.load(department.getId());
    };

    private static <T> CompletableFuture<T> maybeAsyncWithSleep(Supplier<CompletableFuture<T>> supplier) {
        if (useAsyncBatchLoading.get()) {
            return supplyAsyncWithSleep(supplier)
                    .thenCompose(cf -> cf);
        } else {
            return supplier.get();
        }
    }

    private static <T> CompletableFuture<T> supplyAsyncWithSleep(Supplier<T> supplier) {
        Supplier<T> sleepSome = sleepSome(supplier);
        return CompletableFuture.supplyAsync(sleepSome);
    }

    private static <T> Supplier<T> sleepSome(Supplier<T> supplier) {
        return () -> {
            try {
                Thread.sleep(new Random().nextInt(100));
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
