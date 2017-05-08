package graphql.schema.idl;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * This can decompile an in memory GraphQL schema back to a logical schema definition
 */
public class SchemaDecompiler {

    /**
     * Options to use when decompiling a schema
     */
    public static class Options {
        private final boolean includeIntrospectionTypes;

        private final boolean includeScalars;

        private Options(boolean includeIntrospectionTypes, boolean includeScalars) {
            this.includeIntrospectionTypes = includeIntrospectionTypes;
            this.includeScalars = includeScalars;
        }

        public boolean isIncludeIntrospectionTypes() {
            return includeIntrospectionTypes;
        }

        public boolean isIncludeScalars() {
            return includeScalars;
        }

        public static Options defaultOptions() {
            return new Options(false, false);
        }

        /**
         * This will allow you to include introspection types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeIntrospectionTypes(boolean flag) {
            return new Options(flag, this.includeScalars);
        }

        /**
         * This will allow you to include scalar types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeScalarTypes(boolean flag) {
            return new Options(this.includeIntrospectionTypes, flag);
        }
    }

    private final Map<Class, TypePrinter<?>> printers = new LinkedHashMap<>();
    private final Options options;

    public SchemaDecompiler() {
        this(Options.defaultOptions());
    }

    public SchemaDecompiler(Options options) {
        this.options = options;
        printers.put(GraphQLSchema.class, schemaPrinter());
        printers.put(GraphQLObjectType.class, objectPrinter());
        printers.put(GraphQLEnumType.class, enumPrinter());
        printers.put(GraphQLScalarType.class, scalarPrinter());
        printers.put(GraphQLInterfaceType.class, interfacePrinter());
        printers.put(GraphQLUnionType.class, unionPrinter());
        printers.put(GraphQLInputObjectType.class, inputObjectPrinter());
    }

    /**
     * This can decompile an in memory GraphQL schema back to a logical schema definition
     *
     * @param schema the schema in play
     *
     * @return the logical schema definition
     */
    public String decompile(GraphQLSchema schema) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        printer(schema.getClass()).print(out, schema);

        List<GraphQLType> typesAsList = new ArrayList<>(schema.getAllTypesAsList());
        typesAsList.sort(Comparator.comparing(GraphQLType::getName));

        printType(out, typesAsList, GraphQLInputType.class);
        printType(out, typesAsList, GraphQLInterfaceType.class);
        printType(out, typesAsList, GraphQLUnionType.class);
        printType(out, typesAsList, GraphQLObjectType.class);
        printType(out, typesAsList, GraphQLEnumType.class);
        printType(out, typesAsList, GraphQLScalarType.class);

        return sw.toString();
    }
    private interface TypePrinter<T> {

        void print(PrintWriter out, T type);

    }

    private boolean isIntrospectionType(GraphQLType type) {
        return !options.isIncludeIntrospectionTypes() && type.getName().startsWith("__");
    }

    private TypePrinter<GraphQLScalarType> scalarPrinter() {
        return (out, type) -> {
            if (!options.isIncludeScalars()) {
                return;
            }
            if (!ScalarInfo.isStandardScalar(type)) {
                out.format("scalar %s\n\n", type.getName());
            }
        };
    }

    private TypePrinter<GraphQLEnumType> enumPrinter() {
        return (out, type) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            out.format("enum %s {\n", type.getName());
            for (GraphQLEnumValueDefinition enumValueDefinition : type.getValues()) {
                out.format("   %s\n", enumValueDefinition.getName());
            }
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLInterfaceType> interfacePrinter() {
        return (out, type) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            out.format("interface %s {\n", type.getName());
            type.getFieldDefinitions().forEach(fd ->
                    out.format("   %s%s : %s\n",
                            fd.getName(), argsString(fd.getArguments()), typeString(fd.getType())));
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLUnionType> unionPrinter() {
        return (out, type) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            out.format("union %s = ", type.getName());
            List<GraphQLOutputType> types = type.getTypes();
            for (int i = 0; i < types.size(); i++) {
                GraphQLOutputType objectType = types.get(i);
                if (i > 0) {
                    out.format(" | ");
                }
                out.format("%s", objectType.getName());
            }
            out.format("}\n\n");
        };
    }


    private TypePrinter<GraphQLObjectType> objectPrinter() {
        return (out, type) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            out.format("type %s {\n", type.getName());
            type.getFieldDefinitions().forEach(fd ->
                    out.format("   %s%s : %s\n",
                            fd.getName(), argsString(fd.getArguments()), typeString(fd.getType())));
            out.format("}\n\n");
        };
    }


    private TypePrinter<GraphQLInputObjectType> inputObjectPrinter() {
        return (out, type) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            out.format("input %s {\n", type.getName());
            type.getFieldDefinitions().forEach(fd ->
                    out.format("   %s : %s\n",
                            fd.getName(), typeString(fd.getType())));
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLSchema> schemaPrinter() {
        return (out, type) -> {
            out.format("schema {\n");
            GraphQLObjectType queryType = type.getQueryType();
            GraphQLObjectType mutationType = type.getMutationType();
            if (queryType != null) {
                out.format("   query : %s\n", queryType.getName());
            }
            if (mutationType != null) {
                out.format("   mutation : %s\n", mutationType.getName());
            }
            out.format("}\n\n");
        };
    }

    String typeString(GraphQLType rawType) {
        StringBuilder sb = new StringBuilder();
        Stack<String> stack = new Stack<>();

        GraphQLType type = rawType;
        while (true) {
            if (type instanceof GraphQLNonNull) {
                type = ((GraphQLNonNull) type).getWrappedType();
                stack.push("!");
            } else if (type instanceof GraphQLList) {
                type = ((GraphQLList) type).getWrappedType();
                sb.append("[");
                stack.push("]");
            } else {
                sb.append(type.getName());
                break;
            }
        }
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }
        return sb.toString();

    }

    String argsString(List<GraphQLArgument> arguments) {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (GraphQLArgument argument : arguments) {
            if (count == 0) {
                sb.append("(");
            } else {
                sb.append(", ");
            }
            sb.append(argument.getName()).append(" : ").append(typeString(argument.getType()));
            Object defaultValue = argument.getDefaultValue();
            if (defaultValue != null) {
                sb.append(" = ");
                if (defaultValue instanceof Number) {
                    sb.append(defaultValue);
                } else {
                    sb.append('"').append(defaultValue).append('"');
                }
            }
            count++;
        }
        if (count > 0) {
            sb.append(")");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> TypePrinter<T> printer(Class<?> clazz) {
        TypePrinter typePrinter = printers.computeIfAbsent(clazz,
                k -> (out, type) -> out.println("Type not implemented : " + type)
        );
        return (TypePrinter<T>) typePrinter;
    }

    public String decompile(GraphQLType type) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        printType(out, type);

        return sw.toString();
    }

    private void printType(PrintWriter out, List<GraphQLType> typesAsList, Class typeClazz) {
        typesAsList.stream()
                .filter(type -> type.getClass().equals(typeClazz))
                .forEach(type -> printType(out, type));
    }

    private void printType(PrintWriter out, GraphQLType type) {
        TypePrinter<Object> printer = printer(type.getClass());
        printer.print(out, type);
    }
}
