package graphql.schema.idl;

import graphql.Assert;
import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.ValuesResolver;
import graphql.language.AstPrinter;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.DirectiveDefinition;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DefaultGraphqlTypeComparatorRegistry;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorEnvironment;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.InputValueWithState;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.Directives.DeprecatedDirective;
import static graphql.Directives.SpecifiedByDirective;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static graphql.util.EscapeUtil.escapeJsonString;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * This can print an in memory GraphQL schema back to a logical schema definition
 */
@PublicApi
public class SchemaPrinter {
    /**
     * This predicate excludes all directives which are specified by the GraphQL Specification.
     * Printing these directives is optional.
     */
    public static final Predicate<String> ExcludeGraphQLSpecifiedDirectivesPredicate = d -> !Directives.isBuiltInDirective(d);

    /**
     * Options to use when printing a schema
     */
    public static class Options {

        private final boolean includeIntrospectionTypes;

        private final boolean includeScalars;

        private final boolean useAstDefinitions;

        private final boolean includeSchemaDefinition;

        private final boolean includeDirectiveDefinitions;

        private final boolean descriptionsAsHashComments;

        private final Predicate<String> includeDirectiveDefinition;

        private final Predicate<String> includeDirective;

        private final Predicate<GraphQLSchemaElement> includeSchemaElement;

        private final GraphqlTypeComparatorRegistry comparatorRegistry;

        private final boolean includeAstDefinitionComments;

        private Options(boolean includeIntrospectionTypes,
                        boolean includeScalars,
                        boolean includeSchemaDefinition,
                        boolean includeDirectiveDefinitions,
                        Predicate<String> includeDirectiveDefinition,
                        boolean useAstDefinitions,
                        boolean descriptionsAsHashComments,
                        Predicate<String> includeDirective,
                        Predicate<GraphQLSchemaElement> includeSchemaElement,
                        GraphqlTypeComparatorRegistry comparatorRegistry,
                        boolean includeAstDefinitionComments) {
            this.includeIntrospectionTypes = includeIntrospectionTypes;
            this.includeScalars = includeScalars;
            this.includeSchemaDefinition = includeSchemaDefinition;
            this.includeDirectiveDefinitions = includeDirectiveDefinitions;
            this.includeDirectiveDefinition = includeDirectiveDefinition;
            this.includeDirective = includeDirective;
            this.useAstDefinitions = useAstDefinitions;
            this.descriptionsAsHashComments = descriptionsAsHashComments;
            this.comparatorRegistry = comparatorRegistry;
            this.includeSchemaElement = includeSchemaElement;
            this.includeAstDefinitionComments = includeAstDefinitionComments;
        }

        public boolean isIncludeIntrospectionTypes() {
            return includeIntrospectionTypes;
        }

        public boolean isIncludeScalars() {
            return includeScalars;
        }

        public boolean isIncludeSchemaDefinition() {
            return includeSchemaDefinition;
        }

        public boolean isIncludeDirectiveDefinitions() {
            return includeDirectiveDefinitions;
        }

        public Predicate<String> getIncludeDirectiveDefinition() {
            return includeDirectiveDefinition;
        }

        public Predicate<String> getIncludeDirective() {
            return includeDirective;
        }

        public Predicate<GraphQLSchemaElement> getIncludeSchemaElement() {
            return includeSchemaElement;
        }

        public boolean isDescriptionsAsHashComments() {
            return descriptionsAsHashComments;
        }

        public GraphqlTypeComparatorRegistry getComparatorRegistry() {
            return comparatorRegistry;
        }

        public boolean isUseAstDefinitions() {
            return useAstDefinitions;
        }

        public boolean isIncludeAstDefinitionComments() {
            return includeAstDefinitionComments;
        }

        public static Options defaultOptions() {
            return new Options(false,
                    true,
                    false,
                    true,
                    directive -> true, false,
                    false,
                    directive -> true,
                    element -> true,
                    DefaultGraphqlTypeComparatorRegistry.defaultComparators(),
                    false);
        }

        /**
         * This will allow you to include introspection types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeIntrospectionTypes(boolean flag) {
            return new Options(flag,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition, this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * This will allow you to include scalar types that are contained in a schema
         *
         * @param flag whether to include them
         *
         * @return options
         */
        public Options includeScalarTypes(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    flag,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition, this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
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
        public Options includeSchemaDefinition(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    flag,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * This flag controls whether schema printer will include directive definitions at the top of the schema, but does not remove them from the field or type usage.
         * <p>
         * In some schema definitions, like Apollo Federation, the schema should be printed without the directive definitions.
         * This simplified schema is returned by a GraphQL query to other services, in a format that is different that the introspection query.
         * <p>
         * On by default.
         *
         * @param flag whether to print directive definitions
         *
         * @return new instance of options
         */
        public Options includeDirectiveDefinitions(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    flag,
                    directive -> flag,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }


        /**
         * This is a Predicate that decides whether a directive definition is printed.
         *
         * @param includeDirectiveDefinition the predicate to decide of a directive definition is printed
         *
         * @return new instance of options
         */
        public Options includeDirectiveDefinition(Predicate<String> includeDirectiveDefinition) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * Allow to print directives. In some situations, auto-generated schemas contain a lot of directives that
         * make the printout noisy and having this flag would allow cleaner printout. On by default.
         *
         * @param flag whether to print directives
         *
         * @return new instance of options
         */
        public Options includeDirectives(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    directive -> flag,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * This is a Predicate that decides whether a directive element is printed.
         *
         * @param includeDirective the predicate to decide of a directive is printed
         *
         * @return new instance of options
         */
        public Options includeDirectives(Predicate<String> includeDirective) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }


        /**
         * This is a general purpose Predicate that decides whether a schema element is printed ever.
         *
         * @param includeSchemaElement the predicate to decide of a schema is printed
         *
         * @return new instance of options
         */
        public Options includeSchemaElement(Predicate<GraphQLSchemaElement> includeSchemaElement) {
            Assert.assertNotNull(includeSchemaElement);
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * This flag controls whether schema printer will use the {@link graphql.schema.GraphQLType}'s original Ast {@link graphql.language.TypeDefinition}s when printing the type.  This
         * allows access to any `extend type` declarations that might have been originally made.
         *
         * @param flag whether to print via AST type definitions
         *
         * @return new instance of options
         */
        public Options useAstDefinitions(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    flag,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * Descriptions are defined as preceding string literals, however an older legacy
         * versions of SDL supported preceding '#' comments as
         * descriptions. Set this to true to enable this deprecated behavior.
         * This option is provided to ease adoption and may be removed in future versions.
         *
         * @param flag whether to print description as # comments
         *
         * @return new instance of options
         */
        public Options descriptionsAsHashComments(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    flag,
                    this.includeDirective,
                    this.includeSchemaElement,
                    this.comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * The comparator registry controls the printing order for registered {@code GraphQLType}s.
         * <p>
         * The default is to sort elements by name but you can put in your own code to decide on the field order
         *
         * @param comparatorRegistry The registry containing the {@code Comparator} and environment scoping rules.
         *
         * @return options
         */
        public Options setComparators(GraphqlTypeComparatorRegistry comparatorRegistry) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    comparatorRegistry,
                    this.includeAstDefinitionComments);
        }

        /**
         * Sometimes it is useful to allow printing schema comments. This can be achieved by providing comments in the AST definitions.
         * <p>
         * The default is to ignore these for backward compatibility and due to this being relatively uncommon need.
         *
         * @param flag whether to include AST definition comments.
         *
         * @return new instance of Options
         */
        public Options includeAstDefinitionComments(boolean flag) {
            return new Options(this.includeIntrospectionTypes,
                    this.includeScalars,
                    this.includeSchemaDefinition,
                    this.includeDirectiveDefinitions,
                    this.includeDirectiveDefinition,
                    this.useAstDefinitions,
                    this.descriptionsAsHashComments,
                    this.includeDirective,
                    this.includeSchemaElement,
                    comparatorRegistry,
                    flag);
        }
    }

    private final Map<Class<?>, SchemaElementPrinter<?>> printers = new LinkedHashMap<>();

    private final Options options;

    public SchemaPrinter() {
        this(Options.defaultOptions());
    }

    public SchemaPrinter(Options options) {
        this.options = options;
        printers.put(GraphQLSchema.class, schemaPrinter());
        printers.put(GraphQLDirective.class, directivePrinter());
        printers.put(GraphQLObjectType.class, objectPrinter());
        printers.put(GraphQLEnumType.class, enumPrinter());
        printers.put(GraphQLScalarType.class, scalarPrinter());
        printers.put(GraphQLInterfaceType.class, interfacePrinter());
        printers.put(GraphQLUnionType.class, unionPrinter());
        printers.put(GraphQLInputObjectType.class, inputObjectPrinter());
    }

    /**
     * This can print an in memory GraphQL IDL document back to a logical schema definition.
     * If you want to turn an Introspection query result into a Document (and then into a printed
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

        GraphqlFieldVisibility visibility = schema.getCodeRegistry().getFieldVisibility();

        printer(schema.getClass()).print(out, schema, visibility);
        Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLSchemaElement.class, null);

        Stream<? extends GraphQLSchemaElement> directivesAndTypes = Stream.concat(
                schema.getAllTypesAsList().stream(),
                getSchemaDirectives(schema).stream());

        List<GraphQLSchemaElement> elements = directivesAndTypes
                .map(e -> (GraphQLSchemaElement) e)
                .filter(options.getIncludeSchemaElement())
                .sorted(comparator)
                .collect(toList());

        for (GraphQLSchemaElement element : elements) {
            printSchemaElement(out, element, visibility);
        }

        return trimNewLineChars(sw.toString());
    }

    private interface SchemaElementPrinter<T> {

        void print(PrintWriter out, T schemaElement, GraphqlFieldVisibility visibility);

    }

    private boolean isIntrospectionType(GraphQLNamedType type) {
        return !options.isIncludeIntrospectionTypes() && type.getName().startsWith("__");
    }

    private SchemaElementPrinter<GraphQLScalarType> scalarPrinter() {
        return (out, type, visibility) -> {
            if (!options.isIncludeScalars()) {
                return;
            }
            boolean printScalar;
            if (ScalarInfo.isGraphqlSpecifiedScalar(type)) {
                printScalar = false;
                //noinspection RedundantIfStatement
                if (!ScalarInfo.isGraphqlSpecifiedScalar(type)) {
                    printScalar = true;
                }
            } else {
                printScalar = true;
            }
            if (printScalar) {
                if (shouldPrintAsAst(type.getDefinition())) {
                    printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
                } else {
                    printComments(out, type, "");
                    List<GraphQLAppliedDirective> directives = DirectivesUtil.toAppliedDirectives(type).stream()
                            .filter(d -> !d.getName().equals(SpecifiedByDirective.getName()))
                            .collect(toList());
                    out.format("scalar %s%s%s\n\n", type.getName(),
                            directivesString(GraphQLScalarType.class, directives),
                            specifiedByUrlString(type));
                }
            }
        };
    }


    private SchemaElementPrinter<GraphQLEnumType> enumPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLEnumType.class, GraphQLEnumValueDefinition.class);

            if (shouldPrintAsAst(type.getDefinition())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                out.format("enum %s%s", type.getName(), directivesString(GraphQLEnumType.class, type));
                List<GraphQLEnumValueDefinition> values = type.getValues()
                        .stream()
                        .sorted(comparator)
                        .collect(toList());
                if (values.size() > 0) {
                    out.format(" {\n");
                    for (GraphQLEnumValueDefinition enumValueDefinition : values) {
                        printComments(out, enumValueDefinition, "  ");
                        out.format("  %s%s\n", enumValueDefinition.getName(), directivesString(GraphQLEnumValueDefinition.class, enumValueDefinition.isDeprecated(), enumValueDefinition));
                    }
                    out.format("}");
                }
                out.format("\n\n");
            }
        };
    }

    private void printFieldDefinitions(PrintWriter out, Comparator<? super GraphQLSchemaElement> comparator, List<GraphQLFieldDefinition> fieldDefinitions) {
        if (fieldDefinitions.size() == 0) {
            return;
        }

        out.format(" {\n");
        fieldDefinitions
                .stream()
                .filter(options.getIncludeSchemaElement())
                .sorted(comparator)
                .forEach(fd -> {
                    printComments(out, fd, "  ");

                    out.format("  %s%s: %s%s\n",
                            fd.getName(), argsString(GraphQLFieldDefinition.class, fd.getArguments()), typeString(fd.getType()),
                            directivesString(GraphQLFieldDefinition.class, fd.isDeprecated(), fd));
                });
        out.format("}");
    }

    private SchemaElementPrinter<GraphQLInterfaceType> interfacePrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            if (shouldPrintAsAst(type.getDefinition())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                if (type.getInterfaces().isEmpty()) {
                    out.format("interface %s%s", type.getName(), directivesString(GraphQLInterfaceType.class, type));
                } else {

                    Comparator<? super GraphQLSchemaElement> implementsComparator = getComparator(GraphQLInterfaceType.class, GraphQLOutputType.class);

                    Stream<String> interfaceNames = type.getInterfaces()
                            .stream()
                            .sorted(implementsComparator)
                            .map(GraphQLNamedType::getName);
                    out.format("interface %s implements %s%s",
                            type.getName(),
                            interfaceNames.collect(joining(" & ")),
                            directivesString(GraphQLInterfaceType.class, type));
                }

                Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLInterfaceType.class, GraphQLFieldDefinition.class);

                printFieldDefinitions(out, comparator, visibility.getFieldDefinitions(type));
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<GraphQLUnionType> unionPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLUnionType.class, GraphQLOutputType.class);

            if (shouldPrintAsAst(type.getDefinition())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                out.format("union %s%s = ", type.getName(), directivesString(GraphQLUnionType.class, type));
                List<GraphQLNamedOutputType> types = type.getTypes()
                        .stream()
                        .sorted(comparator)
                        .collect(toList());
                for (int i = 0; i < types.size(); i++) {
                    GraphQLNamedOutputType objectType = types.get(i);
                    if (i > 0) {
                        out.format(" | ");
                    }
                    out.format("%s", objectType.getName());
                }
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<GraphQLDirective> directivePrinter() {
        return (out, directive, visibility) -> {
            boolean isOnEver = options.isIncludeDirectiveDefinitions();
            boolean specificTest = options.getIncludeDirectiveDefinition().test(directive.getName());
            if (isOnEver && specificTest) {
                String s = directiveDefinition(directive);
                out.format("%s", s);
                out.print("\n\n");
            }
        };
    }

    private SchemaElementPrinter<GraphQLObjectType> objectPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            if (shouldPrintAsAst(type.getDefinition())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                if (type.getInterfaces().isEmpty()) {
                    out.format("type %s%s", type.getName(), directivesString(GraphQLObjectType.class, type));
                } else {

                    Comparator<? super GraphQLSchemaElement> implementsComparator = getComparator(GraphQLObjectType.class, GraphQLOutputType.class);

                    Stream<String> interfaceNames = type.getInterfaces()
                            .stream()
                            .sorted(implementsComparator)
                            .map(GraphQLNamedType::getName);
                    out.format("type %s implements %s%s",
                            type.getName(),
                            interfaceNames.collect(joining(" & ")),
                            directivesString(GraphQLObjectType.class, type));
                }

                Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLObjectType.class, GraphQLFieldDefinition.class);

                printFieldDefinitions(out, comparator, visibility.getFieldDefinitions(type));
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<GraphQLInputObjectType> inputObjectPrinter() {
        return (out, type, visibility) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            if (shouldPrintAsAst(type.getDefinition())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");

                Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLInputObjectType.class, GraphQLInputObjectField.class);

                out.format("input %s%s", type.getName(), directivesString(GraphQLInputObjectType.class, type));
                List<GraphQLInputObjectField> inputObjectFields = visibility.getFieldDefinitions(type);
                if (inputObjectFields.size() > 0) {
                    out.format(" {\n");
                    inputObjectFields
                            .stream()
                            .filter(options.getIncludeSchemaElement())
                            .sorted(comparator)
                            .forEach(fd -> {
                                printComments(out, fd, "  ");
                                out.format("  %s: %s",
                                        fd.getName(), typeString(fd.getType()));
                                if (fd.hasSetDefaultValue()) {
                                    InputValueWithState defaultValue = fd.getInputFieldDefaultValue();
                                    String astValue = printAst(defaultValue, fd.getType());
                                    out.format(" = %s", astValue);
                                }
                                out.print(directivesString(GraphQLInputObjectField.class, fd.isDeprecated(), fd));
                                out.format("\n");
                            });
                    out.format("}");
                }
                out.format("\n\n");
            }
        };
    }

    /**
     * This will return true if the options say to use the AST and we have an AST element
     *
     * @param definition the AST type definition
     *
     * @return true if we should print using AST nodes
     */
    private boolean shouldPrintAsAst(TypeDefinition<?> definition) {
        return options.isUseAstDefinitions() && definition != null;
    }

    /**
     * This will return true if the options say to use the AST and we have an AST element
     *
     * @param definition the AST schema definition
     *
     * @return true if we should print using AST nodes
     */
    private boolean shouldPrintAsAst(SchemaDefinition definition) {
        return options.isUseAstDefinitions() && definition != null;
    }

    /**
     * This will print out a runtime graphql schema element using its contained AST type definition.  This
     * must be guarded by a called to {@link #shouldPrintAsAst(TypeDefinition)}
     *
     * @param out        the output writer
     * @param definition the AST type definition
     * @param extensions a list of type definition extensions
     */
    private void printAsAst(PrintWriter out, TypeDefinition<?> definition, List<? extends
            TypeDefinition<?>> extensions) {
        out.printf("%s\n", AstPrinter.printAst(definition));
        if (extensions != null) {
            for (TypeDefinition<?> extension : extensions) {
                out.printf("\n%s\n", AstPrinter.printAst(extension));
            }
        }
        out.print('\n');
    }

    /**
     * This will print out a runtime graphql schema block using its AST definition.  This
     * must be guarded by a called to {@link #shouldPrintAsAst(SchemaDefinition)}
     *
     * @param out        the output writer
     * @param definition the AST schema definition
     * @param extensions a list of schema definition extensions
     */
    private void printAsAst(PrintWriter out, SchemaDefinition definition, List<SchemaExtensionDefinition> extensions) {
        out.printf("%s\n", AstPrinter.printAst(definition));
        if (extensions != null) {
            for (SchemaExtensionDefinition extension : extensions) {
                out.printf("\n%s\n", AstPrinter.printAst(extension));
            }
        }
        out.print('\n');
    }


    private static String printAst(InputValueWithState value, GraphQLInputType type) {
        return AstPrinter.printAst(ValuesResolver.valueToLiteral(value, type, GraphQLContext.getDefault(), Locale.getDefault()));
    }

    private SchemaElementPrinter<GraphQLSchema> schemaPrinter() {
        return (out, schema, visibility) -> {
            GraphQLObjectType queryType = schema.getQueryType();
            GraphQLObjectType mutationType = schema.getMutationType();
            GraphQLObjectType subscriptionType = schema.getSubscriptionType();

            // when serializing a GraphQL schema using the type system language, a
            // schema definition should be omitted only if it uses the default root type names.
            boolean needsSchemaPrinted = options.isIncludeSchemaDefinition();

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
                if (shouldPrintAsAst(schema.getDefinition())) {
                    printAsAst(out, schema.getDefinition(), schema.getExtensionDefinitions());
                } else {
                    if (hasAstDefinitionComments(schema) || hasDescription(schema)) {
                        out.print(printComments(schema, ""));
                    }
                    List<GraphQLAppliedDirective> directives = DirectivesUtil.toAppliedDirectives(schema.getSchemaAppliedDirectives(), schema.getSchemaDirectives());
                    out.format("schema %s{\n", directivesString(GraphQLSchemaElement.class, directives));
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
            }
        };
    }

    private List<GraphQLDirective> getSchemaDirectives(GraphQLSchema schema) {
        Predicate<GraphQLDirective> includePredicate = d -> options.getIncludeDirective().test(d.getName());
        return schema.getDirectives().stream()
                .filter(includePredicate)
                .filter(options.getIncludeSchemaElement())
                .collect(toList());
    }

    String typeString(GraphQLType rawType) {
        return GraphQLTypeUtil.simplePrint(rawType);
    }

    String argsString(List<GraphQLArgument> arguments) {
        return argsString(null, arguments);
    }

    String argsString(Class<? extends GraphQLSchemaElement> parent, List<GraphQLArgument> arguments) {
        boolean hasAstDefinitionComments = arguments.stream().anyMatch(this::hasAstDefinitionComments);
        boolean hasDescriptions = arguments.stream().anyMatch(this::hasDescription);
        String halfPrefix = hasAstDefinitionComments || hasDescriptions ? "  " : "";
        String prefix = hasAstDefinitionComments || hasDescriptions ? "    " : "";
        int count = 0;
        StringBuilder sb = new StringBuilder();

        Comparator<? super GraphQLSchemaElement> comparator = getComparator(parent, GraphQLArgument.class);

        arguments = arguments
                .stream()
                .sorted(comparator)
                .filter(options.getIncludeSchemaElement())
                .collect(toList());
        for (GraphQLArgument argument : arguments) {
            if (count == 0) {
                sb.append("(");
            } else {
                sb.append(",");
                if (!hasAstDefinitionComments && !hasDescriptions) {
                    sb.append(" ");
                }
            }
            if (hasAstDefinitionComments || hasDescriptions) {
                sb.append("\n");
            }
            sb.append(printComments(argument, prefix));

            sb.append(prefix).append(argument.getName()).append(": ").append(typeString(argument.getType()));
            if (argument.hasSetDefaultValue()) {
                InputValueWithState defaultValue = argument.getArgumentDefaultValue();
                sb.append(" = ");
                sb.append(printAst(defaultValue, argument.getType()));
            }

            sb.append(directivesString(GraphQLArgument.class, argument.isDeprecated(), argument));

            count++;
        }
        if (count > 0) {
            if (hasAstDefinitionComments || hasDescriptions) {
                sb.append("\n");
            }
            sb.append(halfPrefix).append(")");
        }
        return sb.toString();
    }

    public String directivesString(Class<? extends GraphQLSchemaElement> parentType, GraphQLDirectiveContainer directiveContainer) {
        return directivesString(parentType, false, directiveContainer);
    }

    String directivesString(Class<? extends GraphQLSchemaElement> parentType, boolean isDeprecated, GraphQLDirectiveContainer directiveContainer) {
        List<GraphQLAppliedDirective> directives;
        if (isDeprecated) {
            directives = addOrUpdateDeprecatedDirectiveIfNeeded(directiveContainer);
        } else {
            directives = DirectivesUtil.toAppliedDirectives(directiveContainer);
        }
        return directivesString(parentType, directives);
    }

    private String directivesString(Class<? extends GraphQLSchemaElement> parentType, List<GraphQLAppliedDirective> directives) {
        directives = directives.stream()
                // @deprecated is special - we always print it if something is deprecated
                .filter(directive -> options.getIncludeDirective().test(directive.getName()))
                .filter(options.getIncludeSchemaElement())
                .collect(toList());

        if (directives.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (parentType != GraphQLSchemaElement.class) {
            sb.append(" ");
        }

        Comparator<? super GraphQLSchemaElement> comparator = getComparator(parentType, GraphQLAppliedDirective.class);

        directives = directives
                .stream()
                .sorted(comparator)
                .collect(toList());
        for (int i = 0; i < directives.size(); i++) {
            GraphQLAppliedDirective directive = directives.get(i);
            sb.append(directiveString(directive));
            if (i < directives.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String directiveString(GraphQLAppliedDirective directive) {
        if (!options.getIncludeSchemaElement().test(directive)) {
            return "";
        }
        if (!options.getIncludeDirective().test(directive.getName())) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("@").append(directive.getName());

        Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLAppliedDirective.class, GraphQLAppliedDirectiveArgument.class);

        List<GraphQLAppliedDirectiveArgument> args = directive.getArguments();
        args = args
                .stream()
                .filter(arg -> arg.getArgumentValue().isSet())
                .sorted(comparator)
                .collect(toList());
        if (!args.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < args.size(); i++) {
                GraphQLAppliedDirectiveArgument arg = args.get(i);
                String argValue = null;
                if (arg.hasSetValue()) {
                    argValue = printAst(arg.getArgumentValue(), arg.getType());
                }
                if (!isNullOrEmpty(argValue)) {
                    sb.append(arg.getName());
                    sb.append(" : ");
                    sb.append(argValue);
                    if (i < args.size() - 1) {
                        sb.append(", ");
                    }
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private boolean isDeprecatedDirectiveAllowed() {
        // we ask if the special deprecated directive,
        // which can be programmatically on a type without an applied directive,
        // should be printed or not
        return options.getIncludeDirective().test(DeprecatedDirective.getName());
    }

    private boolean isDeprecatedDirective(GraphQLAppliedDirective directive) {
        return directive.getName().equals(DeprecatedDirective.getName());
    }

    private boolean hasDeprecatedDirective(List<GraphQLAppliedDirective> directives) {
        return directives.stream()
                .filter(this::isDeprecatedDirective)
                .count() == 1;
    }

    private List<GraphQLAppliedDirective> addOrUpdateDeprecatedDirectiveIfNeeded(GraphQLDirectiveContainer directiveContainer) {
        List<GraphQLAppliedDirective> directives = DirectivesUtil.toAppliedDirectives(directiveContainer);
        String reason = getDeprecationReason(directiveContainer);

        if (!hasDeprecatedDirective(directives) && isDeprecatedDirectiveAllowed()) {
            directives = new ArrayList<>(directives);
            directives.add(createDeprecatedDirective(reason));
        } else if (hasDeprecatedDirective(directives) && isDeprecatedDirectiveAllowed()) {
            // Update deprecated reason in case modified by schema transform
            directives = updateDeprecatedDirective(directives, reason);
        }
        return directives;
    }

    private GraphQLAppliedDirective createDeprecatedDirective(String reason) {
        GraphQLAppliedDirectiveArgument arg = GraphQLAppliedDirectiveArgument.newArgument()
                .name("reason")
                .valueProgrammatic(reason)
                .type(GraphQLString)
                .build();
        return GraphQLAppliedDirective.newDirective()
                .name("deprecated")
                .argument(arg)
                .build();
    }

    private List<GraphQLAppliedDirective> updateDeprecatedDirective(List<GraphQLAppliedDirective> directives, String reason) {
        GraphQLAppliedDirectiveArgument newArg = GraphQLAppliedDirectiveArgument.newArgument()
                .name("reason")
                .valueProgrammatic(reason)
                .type(GraphQLString)
                .build();

        return directives.stream().map(d -> {
            if (isDeprecatedDirective(d)) {
                // Don't include reason is deliberately replaced with NOT_SET, for example in Anonymizer
                if (d.getArgument("reason").getArgumentValue() != InputValueWithState.NOT_SET) {
                    return d.transform(builder -> builder.argument(newArg));
                }
            }
            return d;
        }).collect(toList());
    }

    private String getDeprecationReason(GraphQLDirectiveContainer directiveContainer) {
        if (directiveContainer instanceof GraphQLFieldDefinition) {
            GraphQLFieldDefinition type = (GraphQLFieldDefinition) directiveContainer;
            return type.getDeprecationReason();
        } else if (directiveContainer instanceof GraphQLEnumValueDefinition) {
            GraphQLEnumValueDefinition type = (GraphQLEnumValueDefinition) directiveContainer;
            return type.getDeprecationReason();
        } else if (directiveContainer instanceof GraphQLInputObjectField) {
            GraphQLInputObjectField type = (GraphQLInputObjectField) directiveContainer;
            return type.getDeprecationReason();
        } else if (directiveContainer instanceof GraphQLArgument) {
            GraphQLArgument type = (GraphQLArgument) directiveContainer;
            return type.getDeprecationReason();
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private String specifiedByUrlString(GraphQLScalarType scalarType) {
        String url = scalarType.getSpecifiedByUrl();
        if (url == null || !options.getIncludeDirective().test(SpecifiedByDirective.getName())) {
            return "";
        }
        return " @specifiedBy(url : \"" + escapeJsonString(url) + "\")";
    }

    private String directiveDefinition(GraphQLDirective directive) {
        StringBuilder sb = new StringBuilder();

        StringWriter sw = new StringWriter();
        printComments(new PrintWriter(sw), directive, "");

        sb.append(sw);

        sb.append("directive @").append(directive.getName());

        Comparator<? super GraphQLSchemaElement> comparator = getComparator(GraphQLDirective.class, GraphQLArgument.class);

        List<GraphQLArgument> args = directive.getArguments();
        args = args
                .stream()
                .filter(options.getIncludeSchemaElement())
                .sorted(comparator)
                .collect(toList());

        sb.append(argsString(GraphQLDirective.class, args));

        if (directive.isRepeatable()) {
            sb.append(" repeatable");
        }

        sb.append(" on ");

        String locations = directive.validLocations().stream().map(Enum::name).collect(Collectors.joining(" | "));
        sb.append(locations);

        return sb.toString();
    }


    @SuppressWarnings("unchecked")
    private <T> SchemaElementPrinter<T> printer(Class<?> clazz) {
        SchemaElementPrinter<?> schemaElementPrinter = printers.get(clazz);
        if (schemaElementPrinter == null) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz != Object.class) {
                schemaElementPrinter = printer(superClazz);
            } else {
                schemaElementPrinter = (out, type, visibility) -> out.print("Type not implemented : " + type + "\n");
            }
            printers.put(clazz, schemaElementPrinter);
        }
        return (SchemaElementPrinter<T>) schemaElementPrinter;
    }


    public String print(GraphQLType type) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        printSchemaElement(out, type, DEFAULT_FIELD_VISIBILITY);

        return trimNewLineChars(sw.toString());
    }

    public String print(List<GraphQLSchemaElement> elements) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        for (GraphQLSchemaElement element : elements) {
            if (element instanceof GraphQLDirective) {
                out.print(print(((GraphQLDirective) element)));
            } else if (element instanceof GraphQLType) {
                printSchemaElement(out, element, DEFAULT_FIELD_VISIBILITY);
            } else {
                Assert.assertShouldNeverHappen("How did we miss a %s", element.getClass());
            }
        }
        return trimNewLineChars(sw.toString());
    }

    public String print(GraphQLDirective graphQLDirective) {
        return directiveDefinition(graphQLDirective);
    }

    private void printSchemaElement(PrintWriter out, GraphQLSchemaElement schemaElement, GraphqlFieldVisibility visibility) {
        SchemaElementPrinter<Object> printer = printer(schemaElement.getClass());
        printer.print(out, schemaElement, visibility);
    }

    private String printComments(Object graphQLType, String prefix) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        printComments(pw, graphQLType, prefix);
        return sw.toString();
    }

    private void printComments(PrintWriter out, Object graphQLType, String prefix) {
        String descriptionText = getDescription(graphQLType);
        if (!isNullOrEmpty(descriptionText)) {
            List<String> lines = Arrays.asList(descriptionText.split("\n"));
            if (options.isDescriptionsAsHashComments()) {
                printMultiLineHashDescription(out, prefix, lines);
            } else if (!lines.isEmpty()) {
                if (lines.size() > 1) {
                    printMultiLineDescription(out, prefix, lines);
                } else {
                    printSingleLineDescription(out, prefix, lines.get(0));
                }
            }
        }

        if (options.isIncludeAstDefinitionComments()) {
            String commentsText = getAstDefinitionComments(graphQLType);
            if (!isNullOrEmpty(commentsText)) {
                List<String> lines = Arrays.asList(commentsText.split("\n"));
                if (!lines.isEmpty()) {
                    printMultiLineHashDescription(out, prefix, lines);
                }
            }
        }
    }

    private void printMultiLineHashDescription(PrintWriter out, String prefix, List<String> lines) {
        lines.forEach(l -> out.printf("%s#%s\n", prefix, l));
    }

    private void printMultiLineDescription(PrintWriter out, String prefix, List<String> lines) {
        out.printf("%s\"\"\"\n", prefix);
        lines.forEach(l -> {
            String escapedTripleQuotes = l.replaceAll("\"\"\"", "\\\\\"\"\"");
            out.printf("%s%s\n", prefix, escapedTripleQuotes);
        });
        out.printf("%s\"\"\"\n", prefix);
    }

    private void printSingleLineDescription(PrintWriter out, String prefix, String s) {
        // See: https://github.com/graphql/graphql-spec/issues/148
        String desc = escapeJsonString(s);
        out.printf("%s\"%s\"\n", prefix, desc);
    }

    private boolean hasAstDefinitionComments(Object commentHolder) {
        String comments = getAstDefinitionComments(commentHolder);
        return !isNullOrEmpty(comments);
    }

    private String getAstDefinitionComments(Object commentHolder) {
        if (commentHolder instanceof GraphQLObjectType) {
            GraphQLObjectType type = (GraphQLObjectType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(ObjectTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLEnumType) {
            GraphQLEnumType type = (GraphQLEnumType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(EnumTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLFieldDefinition) {
            GraphQLFieldDefinition type = (GraphQLFieldDefinition) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(FieldDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLEnumValueDefinition) {
            GraphQLEnumValueDefinition type = (GraphQLEnumValueDefinition) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(EnumValueDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLUnionType) {
            GraphQLUnionType type = (GraphQLUnionType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(UnionTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType type = (GraphQLInputObjectType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(InputObjectTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLInputObjectField) {
            GraphQLInputObjectField type = (GraphQLInputObjectField) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(InputValueDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType type = (GraphQLInterfaceType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(InterfaceTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLScalarType) {
            GraphQLScalarType type = (GraphQLScalarType) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(ScalarTypeDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLArgument) {
            GraphQLArgument type = (GraphQLArgument) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(InputValueDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLDirective) {
            GraphQLDirective type = (GraphQLDirective) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(DirectiveDefinition::getComments).orElse(null));
        } else if (commentHolder instanceof GraphQLSchema) {
            GraphQLSchema type = (GraphQLSchema) commentHolder;
            return comments(ofNullable(type.getDefinition()).map(SchemaDefinition::getComments).orElse(null));
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private String comments(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        String s = comments.stream().map(c -> c.getContent()).collect(joining("\n", "", "\n"));
        return s;
    }

    private boolean hasDescription(Object descriptionHolder) {
        String description = getDescription(descriptionHolder);
        return !isNullOrEmpty(description);
    }

    private String getDescription(Object descriptionHolder) {
        if (descriptionHolder instanceof GraphQLObjectType) {
            GraphQLObjectType type = (GraphQLObjectType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(ObjectTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLEnumType) {
            GraphQLEnumType type = (GraphQLEnumType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(EnumTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLFieldDefinition) {
            GraphQLFieldDefinition type = (GraphQLFieldDefinition) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(FieldDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLEnumValueDefinition) {
            GraphQLEnumValueDefinition type = (GraphQLEnumValueDefinition) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(EnumValueDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLUnionType) {
            GraphQLUnionType type = (GraphQLUnionType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(UnionTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType type = (GraphQLInputObjectType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(InputObjectTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLInputObjectField) {
            GraphQLInputObjectField type = (GraphQLInputObjectField) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(InputValueDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType type = (GraphQLInterfaceType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(InterfaceTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLScalarType) {
            GraphQLScalarType type = (GraphQLScalarType) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(ScalarTypeDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLArgument) {
            GraphQLArgument type = (GraphQLArgument) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(InputValueDefinition::getDescription).orElse(null));
        } else if (descriptionHolder instanceof GraphQLDirective) {
            GraphQLDirective type = (GraphQLDirective) descriptionHolder;
            return description(type.getDescription(), null);
        } else if (descriptionHolder instanceof GraphQLSchema) {
            GraphQLSchema type = (GraphQLSchema) descriptionHolder;
            return description(type.getDescription(), ofNullable(type.getDefinition()).map(SchemaDefinition::getDescription).orElse(null));
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    String description(String runtimeDescription, Description descriptionAst) {
        //
        // 95% of the time if the schema was built from SchemaGenerator then the runtime description is the only description
        // So the other code here is a really defensive way to get the description
        //
        String descriptionText = runtimeDescription;
        if (isNullOrEmpty(descriptionText)) {
            if (descriptionAst != null) {
                descriptionText = descriptionAst.getContent();
            }
        }
        return descriptionText;
    }

    private Comparator<? super GraphQLSchemaElement> getComparator(Class<? extends GraphQLSchemaElement> parentType, Class<? extends GraphQLSchemaElement> elementType) {
        GraphqlTypeComparatorEnvironment environment = GraphqlTypeComparatorEnvironment.newEnvironment()
                .parentType(parentType)
                .elementType(elementType)
                .build();
        return options.comparatorRegistry.getComparator(environment);
    }

    private static String trimNewLineChars(String s) {
        if (s.endsWith("\n\n")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
