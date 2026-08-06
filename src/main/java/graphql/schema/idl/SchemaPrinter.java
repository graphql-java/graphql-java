package graphql.schema.idl;

import graphql.Assert;
import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.ArrayValue;
import graphql.language.AstPrinter;
import graphql.language.Comment;
import graphql.language.DescribedNode;
import graphql.language.Description;
import graphql.language.DirectiveDefinition;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.schema.DefaultGraphqlTypeComparatorRegistry;
import graphql.schema.ExecutableSchema;
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
import graphql.schema.SchemaAppliedDirective;
import graphql.schema.SchemaAppliedDirectiveArgument;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaDirective;
import graphql.schema.SchemaDirectiveContainer;
import graphql.schema.SchemaEnum;
import graphql.schema.SchemaEnumValue;
import graphql.schema.SchemaField;
import graphql.schema.SchemaFieldsContainer;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaInterface;
import graphql.schema.SchemaList;
import graphql.schema.SchemaModifiedType;
import graphql.schema.SchemaNamedElement;
import graphql.schema.SchemaNamedType;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaObject;
import graphql.schema.SchemaScalar;
import graphql.schema.SchemaType;
import graphql.schema.SchemaUnion;
import graphql.util.FpKit;
import org.jspecify.annotations.Nullable;

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
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
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

    private final Options options;

    public SchemaPrinter() {
        this(Options.defaultOptions());
    }

    public SchemaPrinter(Options options) {
        this.options = options;
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
        return print((ExecutableSchema) schema);
    }

    /**
     * Prints an executable schema view as canonical semantic SDL.
     *
     * @param schema the schema view
     *
     * @return the logical schema definition
     */
    @ExperimentalApi
    public String print(ExecutableSchema schema) {
        ExecutableSchema executableSchema = assertNotNull(schema);
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        printSchema(out, executableSchema);

        List<SchemaNamedElement> elements = new ArrayList<>();
        elements.addAll(executableSchema.getTypes());
        elements.addAll(executableSchema.getDirectives());
        elements.removeIf(element -> !isIncluded(element));
        elements = sort(null, elements, true);
        for (SchemaNamedElement element : elements) {
            printSchemaElement(out, element, executableSchema);
        }
        return trimNewLineChars(sw.toString());
    }

    private interface SchemaElementPrinter<T> {

        void print(
                PrintWriter out,
                T schemaElement,
                @Nullable ExecutableSchema schema);

    }

    private boolean isIntrospectionType(SchemaNamedType type) {
        return !options.isIncludeIntrospectionTypes() && type.getName().startsWith("__");
    }

    private SchemaElementPrinter<SchemaScalar> scalarPrinter() {
        return (out, type, schema) -> {
            if (!options.isIncludeScalars()) {
                return;
            }
            boolean printScalar;
            if (ScalarInfo.isGraphqlSpecifiedScalar(type.getName())) {
                printScalar = false;
                //noinspection RedundantIfStatement
                if (!ScalarInfo.isGraphqlSpecifiedScalar(type.getName())) {
                    printScalar = true;
                }
            } else {
                printScalar = true;
            }
            if (printScalar) {
                if (shouldPrintAsAst(
                        type.getDefinition(),
                        type.getExtensionDefinitions())) {
                    printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
                } else {
                    printComments(out, type, "");
                    List<? extends SchemaAppliedDirective> directives =
                            getAppliedDirectives(schema, type).stream()
                            .filter(d -> !d.getName().equals(SpecifiedByDirective.getName()))
                            .collect(toList());
                    out.format("scalar %s%s%s\n\n", type.getName(),
                            directivesString(
                                    schema,
                                    type,
                                    directives,
                                    false),
                            specifiedByUrlString(type));
                }
            }
        };
    }


    private SchemaElementPrinter<SchemaEnum> enumPrinter() {
        return (out, type, schema) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            if (shouldPrintAsAst(
                    type.getDefinition(),
                    type.getExtensionDefinitions())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                out.format(
                        "enum %s%s",
                        type.getName(),
                        directivesString(
                                schema,
                                type,
                                getAppliedDirectives(schema, type),
                                false));
                List<SchemaEnumValue> values = sort(
                        type,
                        type.getValues(),
                        false);
                if (values.size() > 0) {
                    out.format(" {\n");
                    for (SchemaEnumValue enumValueDefinition : values) {
                        printComments(out, enumValueDefinition, "  ");
                        out.format(
                                "  %s%s\n",
                                enumValueDefinition.getName(),
                                directivesString(
                                        schema,
                                        enumValueDefinition,
                                        getAppliedDirectives(
                                                schema,
                                                enumValueDefinition),
                                        false));
                    }
                    out.format("}");
                }
                out.format("\n\n");
            }
        };
    }

    private void printFieldDefinitions(
            PrintWriter out,
            @Nullable ExecutableSchema schema,
            SchemaFieldsContainer parent,
            List<? extends SchemaField> fieldDefinitions) {
        if (fieldDefinitions.size() == 0) {
            return;
        }

        out.format(" {\n");
        List<SchemaField> fields = sort(
                parent,
                fieldDefinitions,
                false);
        fields.removeIf(field -> !isIncluded(field));
        for (SchemaField field : fields) {
            printComments(out, field, "  ");
            out.format(
                    "  %s%s: %s%s\n",
                    field.getName(),
                    argsString(schema, field),
                    typeString(field.getType()),
                    directivesString(
                            schema,
                            field,
                            getAppliedDirectives(schema, field),
                            false));
        }
        out.format("}");
    }

    private SchemaElementPrinter<SchemaInterface> interfacePrinter() {
        return (out, type, schema) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            if (shouldPrintAsAst(
                    type.getDefinition(),
                    type.getExtensionDefinitions())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                List<? extends SchemaNamedType> interfaces =
                        type.getInterfaces();
                if (interfaces.isEmpty()) {
                    out.format(
                            "interface %s%s",
                            type.getName(),
                            directivesString(
                                    schema,
                                    type,
                                    getAppliedDirectives(schema, type),
                                    false));
                } else {
                    List<SchemaNamedType> sortedInterfaces = sort(
                            type,
                            interfaces,
                            false);
                    Stream<String> interfaceNames = sortedInterfaces.stream()
                            .map(SchemaNamedType::getName);
                    out.format("interface %s implements %s%s",
                            type.getName(),
                            interfaceNames.collect(joining(" & ")),
                            directivesString(
                                    schema,
                                    type,
                                    getAppliedDirectives(schema, type),
                                    false));
                }

                printFieldDefinitions(
                        out,
                        schema,
                        type,
                        getFields(schema, type));
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<SchemaUnion> unionPrinter() {
        return (out, type, schema) -> {
            if (isIntrospectionType(type)) {
                return;
            }

            if (shouldPrintAsAst(
                    type.getDefinition(),
                    type.getExtensionDefinitions())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                out.format(
                        "union %s%s = ",
                        type.getName(),
                        directivesString(
                                schema,
                                type,
                                getAppliedDirectives(schema, type),
                                false));
                List<SchemaNamedType> types = sort(
                        type,
                        type.getTypes(),
                        false);
                for (int i = 0; i < types.size(); i++) {
                    SchemaNamedType objectType = types.get(i);
                    if (i > 0) {
                        out.format(" | ");
                    }
                    out.format("%s", objectType.getName());
                }
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<SchemaDirective> directivePrinter() {
        return (out, directive, schema) -> {
            boolean isOnEver = options.isIncludeDirectiveDefinitions();
            boolean isIncluded =
                    options.getIncludeDirective().test(directive.getName());
            boolean specificTest = options.getIncludeDirectiveDefinition().test(directive.getName());
            if (isOnEver && isIncluded && specificTest) {
                String s = directiveDefinition(schema, directive);
                out.format("%s", s);
                out.print("\n\n");
            }
        };
    }

    private SchemaElementPrinter<SchemaObject> objectPrinter() {
        return (out, type, schema) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            if (shouldPrintAsAst(
                    type.getDefinition(),
                    type.getExtensionDefinitions())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");
                List<? extends SchemaNamedType> interfaces =
                        type.getInterfaces();
                if (interfaces.isEmpty()) {
                    out.format(
                            "type %s%s",
                            type.getName(),
                            directivesString(
                                    schema,
                                    type,
                                    getAppliedDirectives(schema, type),
                                    false));
                } else {
                    List<SchemaNamedType> sortedInterfaces = sort(
                            type,
                            interfaces,
                            false);
                    Stream<String> interfaceNames = sortedInterfaces.stream()
                            .map(SchemaNamedType::getName);
                    out.format("type %s implements %s%s",
                            type.getName(),
                            interfaceNames.collect(joining(" & ")),
                            directivesString(
                                    schema,
                                    type,
                                    getAppliedDirectives(schema, type),
                                    false));
                }

                printFieldDefinitions(
                        out,
                        schema,
                        type,
                        getFields(schema, type));
                out.format("\n\n");
            }
        };
    }

    private SchemaElementPrinter<SchemaInputObject> inputObjectPrinter() {
        return (out, type, schema) -> {
            if (isIntrospectionType(type)) {
                return;
            }
            if (shouldPrintAsAst(
                    type.getDefinition(),
                    type.getExtensionDefinitions())) {
                printAsAst(out, type.getDefinition(), type.getExtensionDefinitions());
            } else {
                printComments(out, type, "");

                out.format(
                        "input %s%s",
                        type.getName(),
                        directivesString(
                                schema,
                                type,
                                getAppliedDirectives(schema, type),
                                false));
                List<SchemaInputField> inputObjectFields = sort(
                        type,
                        getInputFields(schema, type),
                        false);
                if (inputObjectFields.size() > 0) {
                    out.format(" {\n");
                    inputObjectFields.removeIf(field -> !isIncluded(field));
                    for (SchemaInputField field : inputObjectFields) {
                        printComments(out, field, "  ");
                        out.format(
                                "  %s: %s",
                                field.getName(),
                                typeString(field.getType()));
                        InputValueWithState defaultValue =
                                field.getInputFieldDefaultValue();
                        if (defaultValue.isSet()) {
                            out.format(
                                    " = %s",
                                    printValue(
                                            schema,
                                            defaultValue,
                                            field.getType()));
                        }
                        out.print(directivesString(
                                schema,
                                field,
                                getAppliedDirectives(schema, field),
                                false));
                        out.format("\n");
                    }
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
    private boolean shouldPrintAsAst(
            @Nullable Node<?> definition,
            List<? extends Node<?>> extensionDefinitions) {
        return options.isUseAstDefinitions()
                && (definition != null || !extensionDefinitions.isEmpty());
    }

    /**
     * This will print out a runtime graphql schema element using its contained AST type definition.  This
     * must be guarded by a called to {@link #shouldPrintAsAst(TypeDefinition)}
     *
     * @param out        the output writer
     * @param definition the AST type definition
     * @param extensions a list of type definition extensions
     */
    private void printAsAst(
            PrintWriter out,
            @Nullable Node<?> definition,
            List<? extends Node<?>> extensions) {
        boolean printed = false;
        if (definition != null) {
            out.printf("%s\n", AstPrinter.printAst(definition));
            printed = true;
        }
        for (Node<?> extension : extensions) {
            if (printed) {
                out.print('\n');
            }
            out.printf("%s\n", AstPrinter.printAst(extension));
            printed = true;
        }
        out.print('\n');
    }


    private static String printAst(InputValueWithState value, GraphQLInputType type) {
        return AstPrinter.printAst(ValuesResolver.valueToLiteral(value, type, GraphQLContext.getDefault(), Locale.getDefault()));
    }

    private void printSchema(
            PrintWriter out,
            ExecutableSchema schema) {
        SchemaObject queryType = schema.getQueryType();
        SchemaObject mutationType = schema.getMutationType();
        SchemaObject subscriptionType = schema.getSubscriptionType();

        boolean needsSchemaPrinted = options.isIncludeSchemaDefinition()
                || !"Query".equals(queryType.getName());
        if (mutationType != null
                && !"Mutation".equals(mutationType.getName())) {
            needsSchemaPrinted = true;
        }
        if (subscriptionType != null
                && !"Subscription".equals(subscriptionType.getName())) {
            needsSchemaPrinted = true;
        }
        if (!needsSchemaPrinted) {
            return;
        }
        if (shouldPrintAsAst(
                schema.getDefinition(),
                schema.getExtensionDefinitions())) {
            printAsAst(
                    out,
                    schema.getDefinition(),
                    schema.getExtensionDefinitions());
            return;
        }

        printComments(
                out,
                schema.getDescription(),
                schema.getDefinition(),
                "");
        out.format(
                "schema %s{\n",
                directivesString(
                        schema,
                        null,
                        schema.getAppliedDirectives(),
                        true));
        out.format("  query: %s\n", queryType.getName());
        if (mutationType != null) {
            out.format("  mutation: %s\n", mutationType.getName());
        }
        if (subscriptionType != null) {
            out.format("  subscription: %s\n", subscriptionType.getName());
        }
        out.format("}\n\n");
    }

    String typeString(SchemaType type) {
        if (type instanceof SchemaList) {
            return "[" + typeString(
                    ((SchemaList) type).getWrappedType()) + "]";
        }
        if (type instanceof SchemaNonNull) {
            return typeString(
                    ((SchemaNonNull) type).getWrappedType()) + "!";
        }
        return ((SchemaNamedType) type).getName();
    }

    String argsString(List<? extends SchemaArgument> arguments) {
        List<SchemaArgument> sortedArguments = sort(
                null,
                arguments,
                false);
        return formatArgs(null, sortedArguments);
    }

    String argsString(
            @Nullable Class<? extends GraphQLSchemaElement> parentType,
            List<? extends SchemaArgument> arguments) {
        List<SchemaArgument> sortedArguments = sortGraphQL(
                legacyParentType(parentType),
                GraphQLArgument.class,
                arguments);
        return formatArgs(null, sortedArguments);
    }

    private String argsString(
            @Nullable ExecutableSchema schema,
            SchemaNamedElement parent) {
        List<? extends SchemaArgument> arguments;
        if (parent instanceof SchemaField) {
            arguments = ((SchemaField) parent).getArguments();
        } else {
            arguments = ((SchemaDirective) parent).getArguments();
        }
        List<SchemaArgument> sortedArguments = sort(
                parent,
                arguments,
                false);
        return formatArgs(schema, sortedArguments);
    }

    private String formatArgs(
            @Nullable ExecutableSchema schema,
            List<? extends SchemaArgument> arguments) {
        boolean hasAstDefinitionComments = arguments.stream().anyMatch(this::hasAstDefinitionComments);
        boolean hasDescriptions = arguments.stream().anyMatch(this::hasDescription);
        String halfPrefix = hasAstDefinitionComments || hasDescriptions ? "  " : "";
        String prefix = hasAstDefinitionComments || hasDescriptions ? "    " : "";
        int count = 0;
        StringBuilder sb = new StringBuilder();

        List<? extends SchemaArgument> includedArguments = arguments.stream()
                .filter(this::isIncluded)
                .collect(toList());
        for (SchemaArgument argument : includedArguments) {
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
            InputValueWithState defaultValue =
                    argument.getArgumentDefaultValue();
            if (defaultValue.isSet()) {
                sb.append(" = ");
                sb.append(printValue(
                        schema,
                        defaultValue,
                        argument.getType()));
            }

            sb.append(directivesString(
                    schema,
                    argument,
                    getAppliedDirectives(schema, argument),
                    false));

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
        List<SchemaAppliedDirective> included =
                includedDirectives(directives);
        included = sortGraphQL(
                legacyParentType(parentType),
                GraphQLAppliedDirective.class,
                included);
        return formatDirectives(
                null,
                included,
                parentType == GraphQLSchemaElement.class);
    }

    private String directivesString(
            @Nullable ExecutableSchema schema,
            @Nullable SchemaNamedElement parent,
            List<? extends SchemaAppliedDirective> directives,
            boolean schemaContainer) {
        List<SchemaAppliedDirective> included =
                includedDirectives(directives);
        if (included.isEmpty()) {
            return "";
        }
        included = sort(
                parent,
                included,
                false);
        return formatDirectives(schema, included, schemaContainer);
    }

    private List<SchemaAppliedDirective> includedDirectives(
            List<? extends SchemaAppliedDirective> directives) {
        return directives.stream()
                .filter(this::isIncluded)
                .filter(directive -> options.getIncludeDirective()
                        .test(directive.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String formatDirectives(
            @Nullable ExecutableSchema schema,
            List<? extends SchemaAppliedDirective> directives,
            boolean schemaContainer) {
        if (directives.isEmpty()) {
            return "";
        }
        String result = directives.stream()
                .map(directive -> directiveString(schema, directive))
                .collect(joining(" "));
        return schemaContainer ? result : " " + result;
    }

    private String directiveString(
            @Nullable ExecutableSchema schema,
            SchemaAppliedDirective directive) {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(directive.getName());

        List<SchemaAppliedDirectiveArgument> args =
                directive.getArguments().stream()
                .filter(arg -> arg.getArgumentValue().isSet())
                .collect(Collectors.toCollection(ArrayList::new));
        args = sort(
                directive,
                args,
                false);
        if (!args.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < args.size(); i++) {
                SchemaAppliedDirectiveArgument arg = args.get(i);
                String argValue = printValue(
                        schema,
                        arg.getArgumentValue(),
                        arg.getType());
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
        } else if (directiveContainer instanceof GraphQLDirective) {
            GraphQLDirective type = (GraphQLDirective) directiveContainer;
            return type.getDeprecationReason();
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private String specifiedByUrlString(SchemaScalar scalarType) {
        String url = scalarType.getSpecifiedByUrl();
        if (url == null || !options.getIncludeDirective().test(SpecifiedByDirective.getName())) {
            return "";
        }
        return " @specifiedBy(url : \"" + escapeJsonString(url) + "\")";
    }

    private String directiveDefinition(
            @Nullable ExecutableSchema schema,
            SchemaDirective directive) {
        StringBuilder sb = new StringBuilder();

        StringWriter sw = new StringWriter();
        printComments(new PrintWriter(sw), directive, "");

        sb.append(sw);

        sb.append("directive @").append(directive.getName());
        sb.append(argsString(schema, directive));
        sb.append(directivesString(
                schema,
                directive,
                getAppliedDirectives(schema, directive),
                false));

        if (directive.isRepeatable()) {
            sb.append(" repeatable");
        }

        sb.append(" on ");

        String locations = directive.validLocations().stream().map(Enum::name).collect(Collectors.joining(" | "));
        sb.append(locations);

        return sb.toString();
    }

    public String print(GraphQLType type) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        if (type instanceof SchemaNamedElement) {
            printSchemaElement(
                    out,
                    (SchemaNamedElement) type,
                    null);
        } else {
            out.print("Type not implemented : " + type + "\n");
        }

        return trimNewLineChars(sw.toString());
    }

    public String print(List<GraphQLSchemaElement> elements) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        for (GraphQLSchemaElement element : elements) {
            if (element instanceof GraphQLDirective) {
                out.print(print(((GraphQLDirective) element)));
            } else if (element instanceof GraphQLType) {
                printSchemaElement(
                        out,
                        (SchemaNamedElement) element,
                        null);
            } else {
                Assert.assertShouldNeverHappen("How did we miss a %s", element.getClass());
            }
        }
        return trimNewLineChars(sw.toString());
    }

    public String print(GraphQLDirective graphQLDirective) {
        return directiveDefinition(null, graphQLDirective);
    }

    private void printSchemaElement(
            PrintWriter out,
            SchemaNamedElement schemaElement,
            @Nullable ExecutableSchema schema) {
        if (schemaElement instanceof SchemaObject) {
            objectPrinter().print(
                    out,
                    (SchemaObject) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaInterface) {
            interfacePrinter().print(
                    out,
                    (SchemaInterface) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaUnion) {
            unionPrinter().print(
                    out,
                    (SchemaUnion) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaEnum) {
            enumPrinter().print(
                    out,
                    (SchemaEnum) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaScalar) {
            scalarPrinter().print(
                    out,
                    (SchemaScalar) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaInputObject) {
            inputObjectPrinter().print(
                    out,
                    (SchemaInputObject) schemaElement,
                    schema);
            return;
        }
        if (schemaElement instanceof SchemaDirective) {
            directivePrinter().print(
                    out,
                    (SchemaDirective) schemaElement,
                    schema);
            return;
        }
        out.print("Type not implemented : " + schemaElement + "\n");
    }

    private String printComments(
            SchemaNamedElement element,
            String prefix) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        printComments(pw, element, prefix);
        return sw.toString();
    }

    private void printComments(
            PrintWriter out,
            SchemaNamedElement element,
            String prefix) {
        printComments(
                out,
                element.getDescription(),
                element.getDefinition(),
                prefix);
    }

    private void printComments(
            PrintWriter out,
            @Nullable String description,
            @Nullable Node<?> definition,
            String prefix) {
        String descriptionText = description;
        if (isNullOrEmpty(descriptionText)
                && definition instanceof DescribedNode<?>) {
            Description astDescription =
                    ((DescribedNode<?>) definition).getDescription();
            if (astDescription != null) {
                descriptionText = astDescription.getContent();
            }
        }
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
            String commentsText = definition == null
                    ? null
                    : comments(definition.getComments());
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

    private boolean hasAstDefinitionComments(
            SchemaNamedElement element) {
        Node<?> definition = element.getDefinition();
        return definition != null && !definition.getComments().isEmpty();
    }

    private String comments(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        String s = comments.stream().map(c -> c.getContent()).collect(joining("\n", "", "\n"));
        return s;
    }

    private boolean hasDescription(SchemaNamedElement element) {
        String description = getDescription(element);
        return !isNullOrEmpty(description);
    }

    private @Nullable String getDescription(
            SchemaNamedElement element) {
        String runtimeDescription = element.getDescription();
        Node<?> definition = element.getDefinition();
        if (!(definition instanceof DescribedNode<?>)) {
            return runtimeDescription;
        }
        return description(
                runtimeDescription,
                ((DescribedNode<?>) definition).getDescription());
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

    private List<? extends SchemaAppliedDirective> getAppliedDirectives(
            @Nullable ExecutableSchema schema,
            SchemaDirectiveContainer container) {
        if (schema != null) {
            return schema.getAppliedDirectives(container);
        }
        GraphQLDirectiveContainer graphQLContainer =
                (GraphQLDirectiveContainer) container;
        if (isDeprecated(graphQLContainer)) {
            return addOrUpdateDeprecatedDirectiveIfNeeded(graphQLContainer);
        }
        return DirectivesUtil.toAppliedDirectives(graphQLContainer);
    }

    private boolean isDeprecated(
            GraphQLDirectiveContainer container) {
        if (container instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) container).isDeprecated();
        }
        if (container instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) container).isDeprecated();
        }
        if (container instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) container).isDeprecated();
        }
        if (container instanceof GraphQLArgument) {
            return ((GraphQLArgument) container).isDeprecated();
        }
        if (container instanceof GraphQLDirective) {
            return ((GraphQLDirective) container).isDeprecated();
        }
        return false;
    }

    private List<? extends SchemaField> getFields(
            @Nullable ExecutableSchema schema,
            SchemaFieldsContainer type) {
        if (schema != null) {
            return schema.getFields(type);
        }
        if (type instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) type).getFieldDefinitions();
        }
        return ((GraphQLInterfaceType) type).getFieldDefinitions();
    }

    private List<? extends SchemaInputField> getInputFields(
            @Nullable ExecutableSchema schema,
            SchemaInputObject type) {
        if (schema != null) {
            return schema.getInputFields(type);
        }
        return ((GraphQLInputObjectType) type).getFieldDefinitions();
    }

    private String printValue(
            @Nullable ExecutableSchema schema,
            InputValueWithState value,
            SchemaInputType type) {
        if (type instanceof GraphQLType) {
            return AstPrinter.printAst(
                    ValuesResolver.valueToLiteral(
                            value,
                            (GraphQLType) type,
                            GraphQLContext.getDefault(),
                            Locale.getDefault()));
        }
        if (value.isLiteral()) {
            return AstPrinter.printAst(
                    (Value<?>) assertNotNull(value.getValue()));
        }
        return AstPrinter.printAst(valueToLiteral(
                assertNotNull(schema),
                value.getValue(),
                type,
                value.isInternal()));
    }

    private Value<?> valueToLiteral(
            ExecutableSchema schema,
            @Nullable Object value,
            SchemaInputType type,
            boolean internal) {
        SchemaInputType unwrappedType = unwrapNonNull(type);
        if (value == null) {
            return NullValue.of();
        }
        if (unwrappedType instanceof SchemaList) {
            return listLiteral(
                    schema,
                    value,
                    (SchemaList) unwrappedType,
                    internal);
        }
        if (unwrappedType instanceof SchemaInputObject) {
            return objectLiteral(
                    schema,
                    value,
                    (SchemaInputObject) unwrappedType,
                    internal);
        }
        if (unwrappedType instanceof SchemaEnum) {
            return EnumValue.newEnumValue(String.valueOf(value)).build();
        }
        return scalarLiteral(
                schema,
                value,
                (SchemaScalar) unwrappedType,
                internal);
    }

    private SchemaInputType unwrapNonNull(SchemaInputType type) {
        if (!(type instanceof SchemaNonNull)) {
            return type;
        }
        return (SchemaInputType)
                ((SchemaModifiedType) type).getWrappedType();
    }

    private Value<?> listLiteral(
            ExecutableSchema schema,
            Object value,
            SchemaList type,
            boolean internal) {
        SchemaInputType wrappedType =
                (SchemaInputType) type.getWrappedType();
        List<Value> values = new ArrayList<>();
        for (Object item : FpKit.toListOrSingletonList(value)) {
            values.add(valueToLiteral(
                    schema,
                    item,
                    wrappedType,
                    internal));
        }
        return ArrayValue.newArrayValue().values(values).build();
    }

    private Value<?> objectLiteral(
            ExecutableSchema schema,
            Object value,
            SchemaInputObject type,
            boolean internal) {
        if (!(value instanceof Map<?, ?>)) {
            return assertShouldNeverHappen(
                    "Cannot print value '%s' for input object '%s' "
                            + "without a map",
                    value,
                    type.getName());
        }
        Map<?, ?> map = (Map<?, ?>) value;
        List<ObjectField> fields = new ArrayList<>();
        for (SchemaInputField field : schema.getInputFields(type)) {
            if (!map.containsKey(field.getName())) {
                continue;
            }
            fields.add(ObjectField.newObjectField()
                    .name(field.getName())
                    .value(valueToLiteral(
                            schema,
                            map.get(field.getName()),
                            field.getType(),
                            internal))
                    .build());
        }
        return ObjectValue.newObjectValue().objectFields(fields).build();
    }

    private Value<?> scalarLiteral(
            ExecutableSchema schema,
            Object value,
            SchemaScalar type,
            boolean internal) {
        Object externalValue = internal
                ? schema.getScalarCoercing(type).serialize(
                        value,
                        GraphQLContext.getDefault(),
                        Locale.getDefault())
                : value;
        return schema.getScalarCoercing(type).valueToLiteral(
                assertNotNull(externalValue),
                GraphQLContext.getDefault(),
                Locale.getDefault());
    }

    private boolean isIncluded(SchemaNamedElement element) {
        if (!(element instanceof GraphQLSchemaElement)) {
            return true;
        }
        return options.getIncludeSchemaElement()
                .test((GraphQLSchemaElement) element);
    }

    private <T extends SchemaNamedElement> List<T> sort(
            @Nullable SchemaNamedElement parent,
            List<? extends T> elements,
            boolean topLevel) {
        if (elements.size() < 2) {
            return new ArrayList<>(elements);
        }
        if (elements.get(0) instanceof GraphQLSchemaElement) {
            return sortGraphQL(
                    graphQLParentClass(parent),
                    graphQLElementClass(elements.get(0), topLevel),
                    elements);
        }
        List<T> result = new ArrayList<>(elements);
        if (options.getComparatorRegistry()
                == GraphqlTypeComparatorRegistry.AS_IS_REGISTRY) {
            return result;
        }
        Comparator<T> comparator =
                Comparator.comparing(SchemaNamedElement::getName);
        if (topLevel
                && options.getComparatorRegistry()
                != GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY) {
            comparator = Comparator
                    .comparingInt((T element) -> topLevelRank(element))
                    .thenComparing(SchemaNamedElement::getName);
        }
        result.sort(comparator);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T extends SchemaNamedElement> List<T> sortGraphQL(
            Class<? extends GraphQLSchemaElement> parentType,
            @Nullable Class<? extends GraphQLSchemaElement> elementType,
            List<? extends T> elements) {
        List<T> result = new ArrayList<>(elements);
        if (result.size() < 2) {
            return result;
        }
        Comparator comparator = getComparator(parentType, elementType);
        result.sort(comparator);
        return result;
    }

    private Class<? extends GraphQLSchemaElement> legacyParentType(
            @Nullable Class<? extends GraphQLSchemaElement> parentType) {
        return parentType == null
                ? GraphQLSchemaElement.class
                : parentType;
    }

    private Class<? extends GraphQLSchemaElement> graphQLParentClass(
            @Nullable SchemaNamedElement parent) {
        if (parent == null) {
            return GraphQLSchemaElement.class;
        }
        if (parent instanceof SchemaObject) {
            return GraphQLObjectType.class;
        }
        if (parent instanceof SchemaInterface) {
            return GraphQLInterfaceType.class;
        }
        if (parent instanceof SchemaUnion) {
            return GraphQLUnionType.class;
        }
        if (parent instanceof SchemaEnum) {
            return GraphQLEnumType.class;
        }
        if (parent instanceof SchemaEnumValue) {
            return GraphQLEnumValueDefinition.class;
        }
        if (parent instanceof SchemaScalar) {
            return GraphQLScalarType.class;
        }
        if (parent instanceof SchemaInputObject) {
            return GraphQLInputObjectType.class;
        }
        if (parent instanceof SchemaInputField) {
            return GraphQLInputObjectField.class;
        }
        if (parent instanceof SchemaField) {
            return GraphQLFieldDefinition.class;
        }
        if (parent instanceof SchemaArgument) {
            return GraphQLArgument.class;
        }
        if (parent instanceof SchemaDirective) {
            return GraphQLDirective.class;
        }
        if (parent instanceof SchemaAppliedDirective) {
            return GraphQLAppliedDirective.class;
        }
        return GraphQLSchemaElement.class;
    }

    private @Nullable Class<? extends GraphQLSchemaElement>
            graphQLElementClass(
                    SchemaNamedElement element,
                    boolean topLevel) {
        if (topLevel) {
            return null;
        }
        if (element instanceof SchemaObject
                || element instanceof SchemaInterface) {
            return GraphQLOutputType.class;
        }
        if (element instanceof SchemaEnumValue) {
            return GraphQLEnumValueDefinition.class;
        }
        if (element instanceof SchemaInputField) {
            return GraphQLInputObjectField.class;
        }
        if (element instanceof SchemaField) {
            return GraphQLFieldDefinition.class;
        }
        if (element instanceof SchemaArgument) {
            return GraphQLArgument.class;
        }
        if (element instanceof SchemaAppliedDirectiveArgument) {
            return GraphQLAppliedDirectiveArgument.class;
        }
        if (element instanceof SchemaAppliedDirective) {
            return GraphQLAppliedDirective.class;
        }
        if (element instanceof SchemaDirective) {
            return GraphQLDirective.class;
        }
        if (element instanceof SchemaUnion) {
            return GraphQLUnionType.class;
        }
        if (element instanceof SchemaEnum) {
            return GraphQLEnumType.class;
        }
        if (element instanceof SchemaScalar) {
            return GraphQLScalarType.class;
        }
        if (element instanceof SchemaInputObject) {
            return GraphQLInputObjectType.class;
        }
        return GraphQLSchemaElement.class;
    }

    private int topLevelRank(SchemaNamedElement element) {
        if (element instanceof SchemaDirective) {
            return 1;
        }
        if (element instanceof SchemaInterface) {
            return 2;
        }
        if (element instanceof SchemaUnion) {
            return 3;
        }
        if (element instanceof SchemaObject) {
            return 4;
        }
        if (element instanceof SchemaEnum) {
            return 5;
        }
        if (element instanceof SchemaScalar) {
            return 6;
        }
        if (element instanceof SchemaInputObject) {
            return 7;
        }
        return 0;
    }

    private Comparator<? super GraphQLSchemaElement> getComparator(
            Class<? extends GraphQLSchemaElement> parentType,
            @Nullable Class<? extends GraphQLSchemaElement> elementType) {
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
