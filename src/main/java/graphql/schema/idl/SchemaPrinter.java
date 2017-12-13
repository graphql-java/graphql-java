package graphql.schema.idl;

import graphql.Assert;
import graphql.language.Document;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Stream;

import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;


/**
 * This can print an in memory GraphQL schema back to a logical schema definition
 */
public class SchemaPrinter {

    /**
     * Options to use when printing a schema
     */
    public static class Options {
        private final boolean includeIntrospectionTypes;

        private final boolean includeScalars;
        private final boolean includeExtendedScalars;

        private Options(boolean includeIntrospectionTypes, boolean includeScalars, boolean includeExtendedScalars) {
            this.includeIntrospectionTypes = includeIntrospectionTypes;
            this.includeScalars = includeScalars;
            this.includeExtendedScalars = includeExtendedScalars;
        }

        public boolean isIncludeIntrospectionTypes() {
            return includeIntrospectionTypes;
        }

        public boolean isIncludeScalars() {
            return includeScalars;
        }

        public boolean isIncludeExtendedScalars() {
            return includeExtendedScalars;
        }

        public static Options defaultOptions() {
            return new Options(false, false, false);
        }

        /**
         * This will allow you to include introspection types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeIntrospectionTypes(boolean flag) {
            return new Options(flag, this.includeScalars, includeExtendedScalars);
        }

        /**
         * This will allow you to include scalar types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeScalarTypes(boolean flag) {
            return new Options(this.includeIntrospectionTypes, flag, includeExtendedScalars);
        }

        /**
         * This will allow you to include the graphql 'extended' scalar types that come with graphql-java such as
         * GraphQLBigDecimal or GraphQLBigInteger
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeExtendedScalarTypes(boolean flag) {
            return new Options(this.includeIntrospectionTypes, this.includeScalars, flag);
        }
    }

    private final Map<Class, TypePrinter<?>> printers = new LinkedHashMap<>();
    private final Options options;

    public SchemaPrinter() {
        this(Options.defaultOptions());
    }

    public SchemaPrinter(Options options) {
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
     * This can print an in memory GraphQL IDL document back to a logical schema definition.
     *
     * If you want to turn a Introspection query result into a Document (and then into a printed
     * schema) then use {@link graphql.introspection.IntrospectionResultToSchema#createSchemaDefinition(java.util.Map)}
     * first to get the {@link graphql.language.Document} and then print that.
     *
     * @param schemaIDL the parsed schema IDL
     *
     * @return the logical schema definition
     */
    public String print(Document schemaIDL) {
        TypeDefinitionRegistry registry = new SchemaParser().buildRegistry(schemaIDL);
        return print(UnExecutableSchemaGenerator.makeUnExecutableSchema(registry));
    }

    /**
     * This can print an in memory GraphQL schema back to a logical schema definition
     *
     * @param schema the schema in play
     *
     * @return the logical schema definition
     */
    public String print(GraphQLSchema schema) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        GraphqlFieldVisibility visibility = schema.getFieldVisibility();

        printer(schema.getClass()).print(out, schema, visibility);

        List<GraphQLType> typesAsList = schema.getAllTypesAsList()
                .stream()
                .sorted(Comparator.comparing(GraphQLType::getName))
                .collect(toList());

        printType(out, typesAsList, GraphQLInterfaceType.class, visibility);
        printType(out, typesAsList, GraphQLUnionType.class, visibility);
        printType(out, typesAsList, GraphQLObjectType.class, visibility);
        printType(out, typesAsList, GraphQLEnumType.class, visibility);
        printType(out, typesAsList, GraphQLScalarType.class, visibility);
        printType(out, typesAsList, GraphQLInputObjectType.class, visibility);

        String result = sw.toString();
        if (result.endsWith("\n\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private interface TypePrinter<T> {

        void print(PrintWriter out, T type, GraphqlFieldVisibility visibility);

    }

    private boolean isIntrospectionType(GraphQLType type) {
        return !options.isIncludeIntrospectionTypes() && type.getName().startsWith("__");
    }

    private TypePrinter<GraphQLScalarType> scalarPrinter() {
        return (out, type, visibility) -> {
            if (!options.isIncludeScalars()) {
                return;
            }
            boolean printScalar;
            if (ScalarInfo.isStandardScalar(type)) {
                printScalar = false;
                //noinspection RedundantIfStatement
                if (options.isIncludeExtendedScalars() && !ScalarInfo.isGraphqlSpecifiedScalar(type)) {
                    printScalar = true;
                }
            } else {
                printScalar = true;
            }
            if (printScalar) {
                printComments(out, type, "");
                out.format("scalar %s\n\n", type.getName());
            }
        };
    }

    private TypePrinter<GraphQLEnumType> enumPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            out.format("enum %s {\n", type.getName());
            List<GraphQLEnumValueDefinition> values = type.getValues()
                    .stream()
                    .sorted(Comparator.comparing(GraphQLEnumValueDefinition::getName))
                    .collect(toList());
            for (GraphQLEnumValueDefinition enumValueDefinition : values) {
                printComments(out, enumValueDefinition, "  ");
                out.format("  %s\n", enumValueDefinition.getName());
            }
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLInterfaceType> interfacePrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            out.format("interface %s {\n", type.getName());
            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLFieldDefinition::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s%s: %s\n",
                                fd.getName(), argsString(fd.getArguments()), typeString(fd.getType()));
                    });
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLUnionType> unionPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            out.format("union %s = ", type.getName());
            List<GraphQLOutputType> types = type.getTypes()
                    .stream()
                    .sorted(Comparator.comparing(GraphQLOutputType::getName))
                    .collect(toList());
            for (int i = 0; i < types.size(); i++) {
                GraphQLOutputType objectType = types.get(i);
                if (i > 0) {
                    out.format(" | ");
                }
                out.format("%s", objectType.getName());
            }
            out.format("\n\n");
        };
    }


    private TypePrinter<GraphQLObjectType> objectPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            if (type.getInterfaces().isEmpty()) {
                out.format("type %s {\n", type.getName());
            } else {
                Stream<String> interfaceNames = type.getInterfaces()
                        .stream()
                        .map(GraphQLType::getName)
                        .sorted(Comparator.naturalOrder());
                out.format("type %s implements %s {\n",
                        type.getName(),
                        interfaceNames.collect(joining(", ")));
            }

            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLFieldDefinition::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s%s: %s\n",
                                fd.getName(), argsString(fd.getArguments()), typeString(fd.getType()));
                    });
            out.format("}\n\n");
        };
    }


    private TypePrinter<GraphQLInputObjectType> inputObjectPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            out.format("input %s {\n", type.getName());
            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLInputObjectField::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s: %s\n",
                                fd.getName(), typeString(fd.getType()));
                    });
            out.format("}\n\n");
        };
    }

    private TypePrinter<GraphQLSchema> schemaPrinter() {
        return (out, type, visibility) -> {
            GraphQLObjectType queryType = type.getQueryType();
            GraphQLObjectType mutationType = type.getMutationType();
            GraphQLObjectType subscriptionType = type.getSubscriptionType();


            // when serializing a GraphQL schema using the type system language, a
            // schema definition should be omitted if only uses the default root type names.
            boolean needsSchemaPrinted = false;

            if (queryType != null && !queryType.getName().equals("Query")) {
                needsSchemaPrinted = true;
            }
            if (mutationType != null && !mutationType.getName().equals("Mutation")) {
                needsSchemaPrinted = true;
            }
            if (subscriptionType != null && !subscriptionType.getName().equals("Subscription")) {
                needsSchemaPrinted = true;
            }

            if (needsSchemaPrinted) {
                out.format("schema {\n");
                if (queryType != null) {
                    out.format("  query: %s\n", queryType.getName());
                }
                if (mutationType != null) {
                    out.format("  mutation: %s\n", mutationType.getName());
                }
                if (subscriptionType != null) {
                    out.format("  subscription: %s\n", subscriptionType.getName());
                }
                out.format("}\n\n");
            }
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
        boolean hasDescriptions = arguments.stream().filter(arg -> !isNullOrEmpty(arg.getDescription())).count() > 0;
        String prefix = hasDescriptions ? "  " : "";
        int count = 0;
        StringBuilder sb = new StringBuilder();
        arguments = arguments
                .stream()
                .sorted(Comparator.comparing(GraphQLArgument::getName))
                .collect(toList());
        for (GraphQLArgument argument : arguments) {
            if (count == 0) {
                sb.append("(");
            } else {
                sb.append(", ");
            }
            if (hasDescriptions) {
                sb.append("\n");
            }
            String description = argument.getDescription();
            if (!isNullOrEmpty(description)) {
                Stream<String> stream = Arrays.stream(description.split("\n"));
                stream.map(s -> "  #" + s + "\n").forEach(sb::append);
            }
            sb.append(prefix).append(argument.getName()).append(": ").append(typeString(argument.getType()));
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
            if (hasDescriptions) {
                sb.append("\n");
            }
            sb.append(prefix).append(")");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> TypePrinter<T> printer(Class<?> clazz) {
        TypePrinter typePrinter = printers.computeIfAbsent(clazz, k -> {
            Class<?> superClazz = clazz.getSuperclass();
            TypePrinter result;
            if (superClazz != Object.class)
                result = printer(superClazz);
            else
                result = (out, type, visibility) -> out.println("Type not implemented : " + type);
            return result;
        });
        return (TypePrinter<T>) typePrinter;
    }

    public String print(GraphQLType type) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        printType(out, type, DEFAULT_FIELD_VISIBILITY);

        return sw.toString();
    }

    @SuppressWarnings("unchecked")
    private void printType(PrintWriter out, List<GraphQLType> typesAsList, Class typeClazz, GraphqlFieldVisibility visibility) {
        typesAsList.stream()
                .filter(type -> typeClazz.isAssignableFrom(type.getClass()))
                .forEach(type -> printType(out, type, visibility));
    }

    private void printType(PrintWriter out, GraphQLType type, GraphqlFieldVisibility visibility) {
        TypePrinter<Object> printer = printer(type.getClass());
        printer.print(out, type, visibility);
    }


    private void printComments(PrintWriter out, Object graphQLType, String prefix) {
        String description = getDescription(graphQLType);
        if (isNullOrEmpty(description)) {
            return;
        }
        Stream<String> stream = Arrays.stream(description.split("\n"));
        stream.map(s -> prefix + "#" + s + "\n").forEach(out::write);
    }

    private String getDescription(Object descriptionHolder) {
        if (descriptionHolder instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLInputObjectType) {
            return ((GraphQLInputObjectType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLInterfaceType) {
            return ((GraphQLInterfaceType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) descriptionHolder).getDescription();
        } else if (descriptionHolder instanceof GraphQLArgument) {
            return ((GraphQLArgument) descriptionHolder).getDescription();
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
