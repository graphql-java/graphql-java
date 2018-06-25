package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;
import graphql.language.AstPrinter;
import graphql.language.AstValueHelper;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Document;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;


/**
 * This can print an in memory GraphQL schema back to a logical schema definition
 */
@PublicApi
public class SchemaPrinter {

    /**
     * Options to use when printing a schema
     */
    public static class Options {
        private final boolean includeIntrospectionTypes;

        private final boolean includeScalars;
        private final boolean includeExtendedScalars;
        private final boolean includeSchemaDefinition;

        private Options(boolean includeIntrospectionTypes,
                        boolean includeScalars,
                        boolean includeExtendedScalars,
                        boolean includeSchemaDefinition) {
            this.includeIntrospectionTypes = includeIntrospectionTypes;
            this.includeScalars = includeScalars;
            this.includeExtendedScalars = includeExtendedScalars;
            this.includeSchemaDefinition = includeSchemaDefinition;
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

        public boolean isIncludeSchemaDefinition() {
            return includeSchemaDefinition;
        }

        public static Options defaultOptions() {
            return new Options(false, false, false, false);
        }

        /**
         * This will allow you to include introspection types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeIntrospectionTypes(boolean flag) {
            return new Options(flag, this.includeScalars, includeExtendedScalars, this.includeSchemaDefinition);
        }

        /**
         * This will allow you to include scalar types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeScalarTypes(boolean flag) {
            return new Options(this.includeIntrospectionTypes, flag, includeExtendedScalars, this.includeSchemaDefinition);
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
            return new Options(this.includeIntrospectionTypes, this.includeScalars, flag, this.includeSchemaDefinition);
        }

        /**
         * This will force the printing of the graphql schema definition even if the query, mutation, and/or subscription
         * types use the default names.  Some graphql parsers require this information even if the schema uses the
         * default type names.  The schema definition will always be printed if any of the query, mutation, or subscription
         * types do not use the default names.
         *
         * @param flag whether to force include the schema definition
         *
         * @return options
         */
        public Options includeSchemaDefintion(boolean flag) {
            return new Options(this.includeIntrospectionTypes, this.includeScalars, this.includeExtendedScalars, flag);
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
                out.format("scalar %s%s\n\n", type.getName(), directivesString(type.getDirectives()));
            }
        };
    }

    private TypePrinter<GraphQLEnumType> enumPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            printComments(out, type, "");
            out.format("enum %s%s {\n", type.getName(), directivesString(type.getDirectives()));
            List<GraphQLEnumValueDefinition> values = type.getValues()
                    .stream()
                    .sorted(Comparator.comparing(GraphQLEnumValueDefinition::getName))
                    .collect(toList());
            for (GraphQLEnumValueDefinition enumValueDefinition : values) {
                printComments(out, enumValueDefinition, "  ");
                out.format("  %s%s\n", enumValueDefinition.getName(), directivesString(enumValueDefinition.getDirectives()));
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
            out.format("interface %s%s {\n", type.getName(), directivesString(type.getDirectives()));
            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLFieldDefinition::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s%s: %s%s\n",
                                fd.getName(), argsString(fd.getArguments()), typeString(fd.getType()), directivesString(fd.getDirectives()));
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
            out.format("union %s%s = ", type.getName(), directivesString(type.getDirectives()));
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
                out.format("type %s%s {\n", type.getName(), directivesString(type.getDirectives()));
            } else {
                Stream<String> interfaceNames = type.getInterfaces()
                        .stream()
                        .map(GraphQLType::getName)
                        .sorted(Comparator.naturalOrder());
                out.format("type %s implements %s%s {\n",
                        type.getName(),
                        interfaceNames.collect(joining(" & ")),
                        directivesString(type.getDirectives()));
            }

            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLFieldDefinition::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s%s: %s%s\n",
                                fd.getName(), argsString(fd.getArguments()), typeString(fd.getType()), directivesString(fd.getDirectives()));
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
            out.format("input %s%s {\n", type.getName(), directivesString(type.getDirectives()));
            visibility.getFieldDefinitions(type)
                    .stream()
                    .sorted(Comparator.comparing(GraphQLInputObjectField::getName))
                    .forEach(fd -> {
                        printComments(out, fd, "  ");
                        out.format("  %s: %s",
                                fd.getName(), typeString(fd.getType()));
                        Object defaultValue = fd.getDefaultValue();
                        if (defaultValue != null) {
                            String astValue = printAst(defaultValue, fd.getType());
                            out.format(" = %s", astValue);
                        }
                        out.format(directivesString(fd.getDirectives()));
                        out.format("\n");
                    });
            out.format("}\n\n");
        };
    }

    private static String printAst(Object value, GraphQLInputType type) {
        return AstPrinter.printAst(AstValueHelper.astFromValue(value, type));
    }


    private TypePrinter<GraphQLSchema> schemaPrinter() {
        return (out, type, visibility) -> {
            GraphQLObjectType queryType = type.getQueryType();
            GraphQLObjectType mutationType = type.getMutationType();
            GraphQLObjectType subscriptionType = type.getSubscriptionType();


            // when serializing a GraphQL schema using the type system language, a
            // schema definition should be omitted if only uses the default root type names.
            boolean needsSchemaPrinted = options.includeSchemaDefinition;

            if (!needsSchemaPrinted) {
                if (queryType != null && !queryType.getName().equals("Query")) {
                    needsSchemaPrinted = true;
                }
                if (mutationType != null && !mutationType.getName().equals("Mutation")) {
                    needsSchemaPrinted = true;
                }
                if (subscriptionType != null && !subscriptionType.getName().equals("Subscription")) {
                    needsSchemaPrinted = true;
                }
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
        return GraphQLTypeUtil.getUnwrappedTypeName(rawType);
    }

    String argsString(List<GraphQLArgument> arguments) {
        boolean hasDescriptions = arguments.stream().anyMatch(arg -> !isNullOrEmpty(arg.getDescription()));
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
                sb.append(printAst(defaultValue, argument.getType()));
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

    private String directivesString(List<GraphQLDirective> directives) {
        StringBuilder sb = new StringBuilder();
        if (!directives.isEmpty()) {
            sb.append(" ");
        }
        directives = directives
                .stream()
                .sorted(Comparator.comparing(GraphQLDirective::getName))
                .collect(toList());
        for (int i = 0; i < directives.size(); i++) {
            GraphQLDirective directive = directives.get(i);
            sb.append(directiveString(directive));
            if (i < directives.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String directiveString(GraphQLDirective directive) {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(directive.getName());
        List<GraphQLArgument> args = directive.getArguments();
        args = args
                .stream()
                .sorted(Comparator.comparing(GraphQLArgument::getName))
                .collect(toList());
        if (!args.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < args.size(); i++) {
                GraphQLArgument arg = args.get(i);
                sb.append(arg.getName());
                if (arg.getValue() != null) {
                    sb.append(" : ");
                    sb.append(printAst(arg.getValue(), arg.getType()));
                }
                if (arg.getDefaultValue() != null) {
                    sb.append(" = ");
                    sb.append(printAst(arg.getDefaultValue(), arg.getType()));
                }
                if (i < args.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
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

        AstDescriptionAndComments descriptionAndComments = getDescriptionAndComments(graphQLType);
        if (descriptionAndComments == null) {
            return;
        }

        Description astDescription = descriptionAndComments.descriptionAst;
        if (astDescription != null) {
            String quoteStr = "\"";
            if (astDescription.isMultiLine()) {
                quoteStr = "\"\"\"";
            }
            out.write(prefix);
            out.write(quoteStr);
            out.write(astDescription.getContent());
            out.write(quoteStr);
            out.write("\n");

            return;
        }

        if (descriptionAndComments.comments != null) {
            descriptionAndComments.comments.forEach(cmt -> {
                out.write(prefix);
                out.write("#");
                out.write(cmt.getContent());
                out.write("\n");
            });
        } else {
            String runtimeDescription = descriptionAndComments.runtimeDescription;
            if (!isNullOrEmpty(runtimeDescription)) {
                Stream<String> stream = Arrays.stream(runtimeDescription.split("\n"));
                stream.map(s -> prefix + "#" + s + "\n").forEach(out::write);
            }
        }
    }

    static class AstDescriptionAndComments {
        String runtimeDescription;
        Description descriptionAst;
        List<Comment> comments;

        public AstDescriptionAndComments(String runtimeDescription, Description descriptionAst, List<Comment> comments) {
            this.runtimeDescription = runtimeDescription;
            this.descriptionAst = descriptionAst;
            this.comments = comments;
        }
    }

    private AstDescriptionAndComments getDescriptionAndComments(Object descriptionHolder) {
        if (descriptionHolder instanceof GraphQLObjectType) {
            GraphQLObjectType type = (GraphQLObjectType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLEnumType) {
            GraphQLEnumType type = (GraphQLEnumType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLFieldDefinition) {
            GraphQLFieldDefinition type = (GraphQLFieldDefinition) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLEnumValueDefinition) {
            GraphQLEnumValueDefinition type = (GraphQLEnumValueDefinition) descriptionHolder;
            return descriptionAndComments(type::getDescription, () -> null, () -> null);
        } else if (descriptionHolder instanceof GraphQLUnionType) {
            GraphQLUnionType type = (GraphQLUnionType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType type = (GraphQLInputObjectType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLInputObjectField) {
            GraphQLInputObjectField type = (GraphQLInputObjectField) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType type = (GraphQLInterfaceType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLScalarType) {
            GraphQLScalarType type = (GraphQLScalarType) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else if (descriptionHolder instanceof GraphQLArgument) {
            GraphQLArgument type = (GraphQLArgument) descriptionHolder;
            return descriptionAndComments(type::getDescription, type::getDefinition, () -> type.getDefinition().getDescription());
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    AstDescriptionAndComments descriptionAndComments(Supplier<String> stringSupplier, Supplier<Node> nodeSupplier, Supplier<Description> descriptionSupplier) {
        String runtimeDesc = stringSupplier.get();
        Node node = nodeSupplier.get();
        Description description = null;
        List<Comment> comments = null;
        if (node != null) {
            comments = node.getComments();
            description = descriptionSupplier.get();
        }
        return new AstDescriptionAndComments(runtimeDesc, description, comments);

    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
