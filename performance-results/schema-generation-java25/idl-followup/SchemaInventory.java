import graphql.schema.GraphQLSchema;
import graphql.schema.idl.FastSchemaGenerator;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class SchemaInventory {
    public static void main(String[] args) throws Exception {
        for (int number = 1; number <= 5; number++) {
            String name = "large-schema-" + number + ".graphqls";
            String sdl = number == 5 ? load(name + ".part1") + load(name + ".part2") : load(name);
            TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
            int typesBefore = registry.types().size();
            GraphQLSchema standard = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
            print(name, "SchemaGenerator", registry, standard, sdl.length());
            GraphQLSchema fast = new FastSchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
            print(name, "FastSchemaGenerator", registry, fast, sdl.length());
            if (registry.types().size() != typesBefore) {
                throw new AssertionError("Input registry changed");
            }
        }
    }

    private static String load(String name) throws Exception {
        try (InputStream stream = SchemaInventory.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException(name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void print(String name, String generator, TypeDefinitionRegistry registry,
                              GraphQLSchema schema, int characters) throws Exception {
        String printed = new SchemaPrinter().print(schema);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(printed.getBytes(StandardCharsets.UTF_8)));
        System.out.println(name + "\t" + generator + "\t" + characters + "\t" + registry.types().size()
                + "\t" + schema.getTypeMap().size() + "\t" + digest);
    }
}
