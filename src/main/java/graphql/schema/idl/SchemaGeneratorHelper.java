package graphql.schema.idl;

import graphql.AssertException;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.SingletonPropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.TypeResolverProxy;
import graphql.schema.idl.errors.NotAnInputTypeError;
import graphql.schema.idl.errors.NotAnOutputTypeError;
import graphql.util.FpKit;
import graphql.util.Pair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Directives.DEPRECATED_DIRECTIVE_DEFINITION;
import static graphql.Directives.IncludeDirective;
import static graphql.Directives.NO_LONGER_SUPPORTED;
import static graphql.Directives.ONE_OF_DIRECTIVE_DEFINITION;
import static graphql.Directives.SPECIFIED_BY_DIRECTIVE_DEFINITION;
import static graphql.Directives.SkipDirective;
import static graphql.Directives.SpecifiedByDirective;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static graphql.schema.idl.SchemaGeneratorAppliedDirectiveHelper.buildAppliedDirectives;
import static graphql.schema.idl.SchemaGeneratorAppliedDirectiveHelper.buildDirectiveDefinitionFromAst;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;

@Internal
public class SchemaGeneratorHelper {

    /**
     * We pass this around so we know what we have defined in a stack like manner plus
     * it gives us helper functions
     */
    static class BuildContext {
        private final ImmutableTypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring wiring;
        private final Deque<String> typeStack = new ArrayDeque<>();

        private final Map<String, GraphQLNamedOutputType> outputGTypes = new LinkedHashMap<>();
        private final Map<String, GraphQLNamedInputType> inputGTypes = new LinkedHashMap<>();
        private final Set<GraphQLDirective> directives = new LinkedHashSet<>();
        private final GraphQLCodeRegistry.Builder codeRegistry;
        public final Map<String, OperationTypeDefinition> operationTypeDefs;
        public final SchemaGenerator.Options options;
        public boolean directiveWiringRequired;

        BuildContext(ImmutableTypeDefinitionRegistry typeRegistry, RuntimeWiring wiring, Map<String, OperationTypeDefinition> operationTypeDefinitions, SchemaGenerator.Options options) {
            this.typeRegistry = typeRegistry;
            this.wiring = wiring;
            this.codeRegistry = GraphQLCodeRegistry.newCodeRegistry(wiring.getCodeRegistry());
            this.operationTypeDefs = operationTypeDefinitions;
            this.options = options;
            directiveWiringRequired = false;
        }

        public boolean isDirectiveWiringRequired() {
            return directiveWiringRequired;
        }

        public TypeDefinitionRegistry getTypeRegistry() {
            return typeRegistry;
        }

        TypeDefinition<?> getTypeDefinition(Type<?> type) {
            TypeDefinition<?> typeDefinition = typeRegistry.getTypeOrNull(type);
            if (typeDefinition != null) {
                return typeDefinition;
            } else {
                throw new AssertException(format(" type definition for type '%s' not found", type));
            }
        }

        boolean stackContains(TypeInfo typeInfo) {
            return typeStack.contains(typeInfo.getName());
        }

        void push(TypeInfo typeInfo) {
            typeStack.push(typeInfo.getName());
        }

        void pop() {
            typeStack.pop();
        }

        GraphQLOutputType hasOutputType(TypeDefinition<?> typeDefinition) {
            return outputGTypes.get(typeDefinition.getName());
        }

        GraphQLInputType hasInputType(TypeDefinition<?> typeDefinition) {
            return inputGTypes.get(typeDefinition.getName());
        }

        void putOutputType(GraphQLNamedOutputType outputType) {
            outputGTypes.put(outputType.getName(), outputType);
            // certain types can be both input and output types, for example enums and scalars
            if (outputType instanceof GraphQLInputType) {
                inputGTypes.put(outputType.getName(), (GraphQLNamedInputType) outputType);
            }
        }

        void putInputType(GraphQLNamedInputType inputType) {
            inputGTypes.put(inputType.getName(), inputType);
            // certain types can be both input and output types, for example enums and scalars
            if (inputType instanceof GraphQLOutputType) {
                outputGTypes.put(inputType.getName(), (GraphQLNamedOutputType) inputType);
            }
        }

        RuntimeWiring getWiring() {
            return wiring;
        }

        GraphqlTypeComparatorRegistry getComparatorRegistry() {
            return wiring.getComparatorRegistry();
        }

        public GraphQLCodeRegistry.Builder getCodeRegistry() {
            return codeRegistry;
        }

        public void addDirectiveDefinition(GraphQLDirective directive) {
            this.directives.add(directive);
        }

        public void addDirectives(Set<GraphQLDirective> directives) {
            this.directives.addAll(directives);
        }

        public Set<GraphQLDirective> getDirectives() {
            return directives;
        }

        /**
         * Returns all types that have been built so far (both input and output types).
         * This is used by FastSchemaGenerator to collect types for FastBuilder.
         */
        public Set<GraphQLType> getTypes() {
            Set<GraphQLType> allTypes = new LinkedHashSet<>();
            allTypes.addAll(outputGTypes.values());
            allTypes.addAll(inputGTypes.values());
            return allTypes;
        }

        public boolean isCaptureAstDefinitions() {
            return options.isCaptureAstDefinitions();
        }
    }

    static String buildDescription(BuildContext buildContext, Node<?> node, Description description) {
        if (description != null) {
            return description.getContent();
        }
        if (!buildContext.options.isUseCommentsAsDescription()) {
            return null;
        }
        List<Comment> comments = node.getComments();
        List<String> lines = new ArrayList<>();
        for (Comment comment : comments) {
            String commentLine = comment.getContent();
            if (commentLine.trim().isEmpty()) {
                lines.clear();
            } else {
                lines.add(commentLine);
            }
        }
        if (lines.size() == 0) {
            return null;
        }
        return String.join("\n", lines);
    }

    String buildDeprecationReason(List<Directive> directives) {
        directives = Optional.ofNullable(directives).orElse(emptyList());
        Optional<Directive> directive = directives.stream().filter(d -> "deprecated".equals(d.getName())).findFirst();
        if (directive.isPresent()) {
            Map<String, String> args = directive.get().getArguments().stream().collect(toMap(
                    Argument::getName, arg -> ((StringValue) arg.getValue()).getValue()
            ));
            if (args.isEmpty()) {
                return NO_LONGER_SUPPORTED; // default value from spec
            } else {
                // pre flight checks have ensured it's valid
                return args.get("reason");
            }
        }
        return null;
    }

    public Function<Type<?>, GraphQLInputType> inputTypeFactory(BuildContext buildCtx) {
        return rawType -> buildInputType(buildCtx, rawType);
    }

    GraphQLInputType buildInputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition<?> typeDefinition = buildCtx.getTypeDefinition(rawType);
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        GraphQLInputType inputType = buildCtx.hasInputType(typeDefinition);
        if (inputType != null) {
            return typeInfo.decorate(inputType);
        }

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(typeRef(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof InputObjectTypeDefinition) {
            inputType = buildInputObjectType(buildCtx, (InputObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            inputType = buildEnumType(buildCtx, (EnumTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            inputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        } else {
            // typeDefinition is not a valid InputType
            throw new NotAnInputTypeError(rawType, typeDefinition);
        }

        buildCtx.putInputType((GraphQLNamedInputType) inputType);
        buildCtx.pop();
        return typeInfo.decorate(inputType);
    }

    GraphQLInputObjectType buildInputObjectType(BuildContext buildCtx, InputObjectTypeDefinition typeDefinition) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(buildCtx, typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<InputObjectTypeExtensionDefinition> extensions = inputObjectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList());


        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(buildCtx,
                inputTypeFactory(buildCtx),
                typeDefinition.getDirectives(),
                directivesOf(extensions),
                INPUT_OBJECT,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());

        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        typeDefinition.getInputValueDefinitions().forEach(inputValue ->
                builder.field(buildInputField(buildCtx, inputValue)));

        extensions.forEach(extension -> extension.getInputValueDefinitions().forEach(inputValueDefinition -> {
            GraphQLInputObjectField inputField = buildInputField(buildCtx, inputValueDefinition);
            if (!builder.hasField(inputField.getName())) {
                builder.field(inputField);
            }
        }));

        return directivesObserve(buildCtx, builder.build());
    }

    private GraphQLInputObjectField buildInputField(BuildContext buildCtx, InputValueDefinition fieldDef) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
        fieldBuilder.definition(buildCtx.isCaptureAstDefinitions() ? fieldDef : null);
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description(buildDescription(buildCtx, fieldDef, fieldDef.getDescription()));
        fieldBuilder.deprecate(buildDeprecationReason(fieldDef.getDirectives()));
        fieldBuilder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLInputType inputType = buildInputType(buildCtx, fieldDef.getType());
        fieldBuilder.type(inputType);
        Value<?> defaultValue = fieldDef.getDefaultValue();
        if (defaultValue != null) {
            fieldBuilder.defaultValueLiteral(defaultValue);
        }

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                fieldDef.getDirectives(),
                emptyList(),
                INPUT_FIELD_DEFINITION,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());

        buildAppliedDirectives(buildCtx, fieldBuilder, appliedDirectives);

        return directivesObserve(buildCtx, fieldBuilder.build());
    }

    GraphQLEnumType buildEnumType(BuildContext buildCtx, EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(buildCtx, typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<EnumTypeExtensionDefinition> extensions = enumTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList());

        EnumValuesProvider enumValuesProvider = buildCtx.getWiring().getEnumValuesProviders().get(typeDefinition.getName());
        typeDefinition.getEnumValueDefinitions().forEach(evd -> {
            GraphQLEnumValueDefinition enumValueDefinition = buildEnumValue(buildCtx, typeDefinition, enumValuesProvider, evd);
            builder.value(enumValueDefinition);
        });

        extensions.forEach(extension -> extension.getEnumValueDefinitions().forEach(evd -> {
            GraphQLEnumValueDefinition enumValueDefinition = buildEnumValue(buildCtx, typeDefinition, enumValuesProvider, evd);
            if (!builder.hasValue(enumValueDefinition.getName())) {
                builder.value(enumValueDefinition);
            }
        }));

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                typeDefinition.getDirectives(),
                directivesOf(extensions),
                ENUM,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());

        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        return directivesObserve(buildCtx, builder.build());
    }

    private GraphQLEnumValueDefinition buildEnumValue(BuildContext buildCtx,
                                                      EnumTypeDefinition typeDefinition,
                                                      EnumValuesProvider enumValuesProvider,
                                                      EnumValueDefinition evd) {
        String description = buildDescription(buildCtx, evd, evd.getDescription());
        String deprecation = buildDeprecationReason(evd.getDirectives());

        Object value;
        if (enumValuesProvider != null) {
            value = enumValuesProvider.getValue(evd.getName());
            assertNotNull(value,
                    "EnumValuesProvider for %s returned null for %s", typeDefinition.getName(), evd.getName());
        } else {
            value = evd.getName();
        }
        GraphQLEnumValueDefinition.Builder builder = newEnumValueDefinition()
                .name(evd.getName())
                .value(value)
                .description(description)
                .deprecationReason(deprecation)
                .definition(buildCtx.isCaptureAstDefinitions() ? evd : null)
                .comparatorRegistry(buildCtx.getComparatorRegistry());

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                evd.getDirectives(),
                emptyList(),
                ENUM_VALUE,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());

        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        GraphQLEnumValueDefinition enumValueDefinition = builder.build();
        return directivesObserve(buildCtx, enumValueDefinition);
    }

    GraphQLScalarType buildScalar(BuildContext buildCtx, ScalarTypeDefinition typeDefinition) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring runtimeWiring = buildCtx.getWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        List<ScalarTypeExtensionDefinition> extensions = scalarTypeExtensions(typeDefinition, buildCtx);

        ScalarWiringEnvironment environment = new ScalarWiringEnvironment(typeRegistry, typeDefinition, extensions);

        GraphQLScalarType scalar;
        if (wiringFactory.providesScalar(environment)) {
            scalar = wiringFactory.getScalar(environment);
        } else {
            scalar = buildCtx.getWiring().getScalars().get(typeDefinition.getName());
        }

        if (!ScalarInfo.isGraphqlSpecifiedScalar(scalar)) {
            String description = getScalarDesc(scalar, typeDefinition);

            Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                    buildCtx,
                    inputTypeFactory(buildCtx),
                    typeDefinition.getDirectives(),
                    directivesOf(extensions),
                    SCALAR,
                    buildCtx.getDirectives(),
                    buildCtx.getComparatorRegistry());

            scalar = scalar.transform(builder -> {
                builder
                        .description(description)
                        .definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null)
                        .comparatorRegistry(buildCtx.getComparatorRegistry())
                        .specifiedByUrl(getSpecifiedByUrl(typeDefinition, extensions));

                buildAppliedDirectives(buildCtx, builder, appliedDirectives);
            });
        }
        return directivesObserve(buildCtx, scalar);
    }

    private String getScalarDesc(GraphQLScalarType scalar, ScalarTypeDefinition typeDefinition) {
        if (scalar.getDescription() != null) {
            if (!scalar.getDescription().trim().isEmpty()) {
                return scalar.getDescription();
            }
        }
        if (typeDefinition.getDescription() != null) {
            return typeDefinition.getDescription().getContent();
        }
        return "";
    }

    String getSpecifiedByUrl(ScalarTypeDefinition scalarTypeDefinition, List<ScalarTypeExtensionDefinition> extensions) {
        List<Directive> allDirectives = new ArrayList<>(scalarTypeDefinition.getDirectives());
        extensions.forEach(extension -> allDirectives.addAll(extension.getDirectives()));
        Optional<Directive> specifiedByDirective = FpKit.findOne(allDirectives,
                directiveDefinition -> directiveDefinition.getName().equals(SpecifiedByDirective.getName()));
        if (!specifiedByDirective.isPresent()) {
            return null;
        }
        Argument urlArgument = specifiedByDirective.get().getArgument("url");
        StringValue url = (StringValue) urlArgument.getValue();
        return url.getValue();
    }

    private TypeResolver getTypeResolverForInterface(BuildContext buildCtx, InterfaceTypeDefinition interfaceType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;

        InterfaceWiringEnvironment environment = new InterfaceWiringEnvironment(typeRegistry, interfaceType);

        if (wiringFactory.providesTypeResolver(environment)) {
            typeResolver = wiringFactory.getTypeResolver(environment);
            assertNotNull(typeResolver, "The WiringFactory indicated it provides a interface type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(interfaceType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }
        return typeResolver;
    }

    private TypeResolver getTypeResolverForUnion(BuildContext buildCtx, UnionTypeDefinition unionType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;
        UnionWiringEnvironment environment = new UnionWiringEnvironment(typeRegistry, unionType);

        if (wiringFactory.providesTypeResolver(environment)) {
            typeResolver = wiringFactory.getTypeResolver(environment);
            assertNotNull(typeResolver, "The WiringFactory indicated it union provides a type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(unionType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }

        return typeResolver;
    }

    private void buildInterfaceTypeInterfaces(BuildContext buildCtx,
                                              InterfaceTypeDefinition typeDefinition,
                                              GraphQLInterfaceType.Builder builder,
                                              List<InterfaceTypeExtensionDefinition> extensions) {
        Map<String, GraphQLOutputType> interfaces = new LinkedHashMap<>();
        typeDefinition.getImplements().forEach(type -> {
            GraphQLNamedOutputType newInterfaceType = buildOutputType(buildCtx, type);
            interfaces.put(newInterfaceType.getName(), newInterfaceType);
        });

        extensions.forEach(extension -> extension.getImplements().forEach(type -> {
            GraphQLNamedOutputType interfaceType = buildOutputType(buildCtx, type);
            if (!interfaces.containsKey(interfaceType.getName())) {
                interfaces.put(interfaceType.getName(), interfaceType);
            }
        }));

        interfaces.values().forEach(interfaze -> {
            if (interfaze instanceof GraphQLInterfaceType) {
                builder.withInterface((GraphQLInterfaceType) interfaze);
                return;
            }
            if (interfaze instanceof GraphQLTypeReference) {
                builder.withInterface((GraphQLTypeReference) interfaze);
            }
        });
    }

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        return buildOutputType(buildCtx, operation.getTypeName());
    }

    GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(buildCtx, typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<InterfaceTypeExtensionDefinition> extensions = interfaceTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList());

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                typeDefinition.getDirectives(),
                directivesOf(extensions),
                OBJECT,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            builder.field(fieldDefinition);
        });

        extensions.forEach(extension -> extension.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            if (!builder.hasField(fieldDefinition.getName())) {
                builder.field(fieldDefinition);
            }
        }));

        buildInterfaceTypeInterfaces(buildCtx, typeDefinition, builder, extensions);

        GraphQLInterfaceType interfaceType = builder.build();
        if (!buildCtx.getCodeRegistry().hasTypeResolver(interfaceType.getName())) {
            TypeResolver typeResolver = getTypeResolverForInterface(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(interfaceType, typeResolver);
        }
        return directivesObserve(buildCtx, interfaceType);
    }

    GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(buildCtx, typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<ObjectTypeExtensionDefinition> extensions = objectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList());
        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                typeDefinition.getDirectives(),
                directivesOf(extensions),
                OBJECT,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            builder.field(fieldDefinition);
        });

        extensions.forEach(extension -> extension.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            if (!builder.hasField(fieldDefinition.getName())) {
                builder.field(fieldDefinition);
            }
        }));

        buildObjectTypeInterfaces(buildCtx, typeDefinition, builder, extensions);

        return directivesObserve(buildCtx, builder.build());
    }

    private void buildObjectTypeInterfaces(BuildContext buildCtx,
                                           ObjectTypeDefinition typeDefinition,
                                           GraphQLObjectType.Builder builder,
                                           List<ObjectTypeExtensionDefinition> extensions) {
        Map<String, GraphQLOutputType> interfaces = new LinkedHashMap<>();
        typeDefinition.getImplements().forEach(type -> {
            GraphQLNamedOutputType newInterfaceType = buildOutputType(buildCtx, type);
            interfaces.put(newInterfaceType.getName(), newInterfaceType);
        });

        extensions.forEach(extension -> extension.getImplements().forEach(type -> {
            GraphQLNamedOutputType interfaceType = buildOutputType(buildCtx, type);
            if (!interfaces.containsKey(interfaceType.getName())) {
                interfaces.put(interfaceType.getName(), interfaceType);
            }
        }));

        interfaces.values().forEach(interfaze -> {
            if (interfaze instanceof GraphQLInterfaceType) {
                builder.withInterface((GraphQLInterfaceType) interfaze);
                return;
            }
            if (interfaze instanceof GraphQLTypeReference) {
                builder.withInterface((GraphQLTypeReference) interfaze);
            }
        });
    }

    GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? typeDefinition : null);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(buildCtx, typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<UnionTypeExtensionDefinition> extensions = unionTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList());

        typeDefinition.getMemberTypes().forEach(mt -> {
            GraphQLOutputType outputType = buildOutputType(buildCtx, mt);
            if (outputType instanceof GraphQLTypeReference) {
                builder.possibleType((GraphQLTypeReference) outputType);
            } else {
                builder.possibleType((GraphQLObjectType) outputType);
            }
        });

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                typeDefinition.getDirectives(),
                directivesOf(extensions),
                UNION,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        extensions.forEach(extension -> extension.getMemberTypes().forEach(mt -> {
                    GraphQLNamedOutputType outputType = buildOutputType(buildCtx, mt);
                    if (!builder.containType(outputType.getName())) {
                        if (outputType instanceof GraphQLTypeReference) {
                            builder.possibleType((GraphQLTypeReference) outputType);
                        } else {
                            builder.possibleType((GraphQLObjectType) outputType);
                        }
                    }
                }
        ));

        GraphQLUnionType unionType = builder.build();
        if (!buildCtx.getCodeRegistry().hasTypeResolver(unionType.getName())) {
            TypeResolver typeResolver = getTypeResolverForUnion(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(unionType, typeResolver);
        }
        return directivesObserve(buildCtx, unionType);
    }

    /**
     * This is the main recursive spot that builds out the various forms of Output types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @param rawType  the type to be built
     *
     * @return an output type
     */
    @SuppressWarnings({"TypeParameterUnusedInFormals"})
    private <T extends GraphQLOutputType> T buildOutputType(BuildContext buildCtx, Type<?> rawType) {

        TypeDefinition<?> typeDefinition = buildCtx.getTypeDefinition(rawType);
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        GraphQLOutputType outputType = buildCtx.hasOutputType(typeDefinition);
        if (outputType != null) {
            return typeInfo.decorate(outputType);
        }

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it up later
            // otherwise we will go into an infinite loop
            return typeInfo.decorate(typeRef(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof ObjectTypeDefinition) {
            outputType = buildObjectType(buildCtx, (ObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            outputType = buildInterfaceType(buildCtx, (InterfaceTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            outputType = buildUnionType(buildCtx, (UnionTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            outputType = buildEnumType(buildCtx, (EnumTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            outputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        } else {
            // typeDefinition is not a valid output type
            throw new NotAnOutputTypeError(rawType, typeDefinition);
        }

        buildCtx.putOutputType((GraphQLNamedOutputType) outputType);
        buildCtx.pop();
        return typeInfo.decorate(outputType);
    }

    GraphQLFieldDefinition buildField(BuildContext buildCtx, TypeDefinition<?> parentType, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? fieldDef : null);
        builder.name(fieldDef.getName());
        builder.description(buildDescription(buildCtx, fieldDef, fieldDef.getDescription()));
        builder.deprecate(buildDeprecationReason(fieldDef.getDirectives()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                fieldDef.getDirectives(),
                emptyList(), FIELD_DEFINITION,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());

        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        fieldDef.getInputValueDefinitions().forEach(inputValueDefinition ->
                builder.argument(buildArgument(buildCtx, inputValueDefinition)));

        GraphQLOutputType fieldType = buildOutputType(buildCtx, fieldDef.getType());
        builder.type(fieldType);

        GraphQLFieldDefinition fieldDefinition = builder.build();
        // if they have already wired in a fetcher - then leave it alone
        FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType.getName(), fieldDefinition.getName());
        if (!buildCtx.getCodeRegistry().hasDataFetcher(coordinates)) {
            Optional<DataFetcherFactory<?>> dataFetcherFactory = buildDataFetcherFactory(buildCtx,
                    parentType,
                    fieldDef,
                    fieldType,
                    appliedDirectives.first,
                    appliedDirectives.second);

            // if the dataFetcherFactory is empty, then it must have been the code registry default one
            // and hence we don't need to make a "map entry" in the code registry since it will be defaulted
            // anyway
            dataFetcherFactory.ifPresent(fetcherFactory -> buildCtx.getCodeRegistry().dataFetcher(coordinates, fetcherFactory));
        }
        return directivesObserve(buildCtx, fieldDefinition);
    }

    private Optional<DataFetcherFactory<?>> buildDataFetcherFactory(BuildContext buildCtx,
                                                                    TypeDefinition<?> parentType,
                                                                    FieldDefinition fieldDef,
                                                                    GraphQLOutputType fieldType,
                                                                    List<GraphQLDirective> directives,
                                                                    List<GraphQLAppliedDirective> appliedDirectives) {
        String fieldName = fieldDef.getName();
        String parentTypeName = parentType.getName();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring runtimeWiring = buildCtx.getWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        GraphQLCodeRegistry.Builder codeRegistry = buildCtx.getCodeRegistry();

        FieldWiringEnvironment wiringEnvironment = new FieldWiringEnvironment(typeRegistry, parentType, fieldDef, fieldType, directives, appliedDirectives);

        DataFetcherFactory<?> dataFetcherFactory;
        if (wiringFactory.providesDataFetcherFactory(wiringEnvironment)) {
            dataFetcherFactory = wiringFactory.getDataFetcherFactory(wiringEnvironment);
            assertNotNull(dataFetcherFactory, "The WiringFactory indicated it provides a data fetcher factory but then returned null");
        } else {
            //
            // ok they provide a data fetcher directly
            DataFetcher<?> dataFetcher;
            if (wiringFactory.providesDataFetcher(wiringEnvironment)) {
                dataFetcher = wiringFactory.getDataFetcher(wiringEnvironment);
                assertNotNull(dataFetcher, "The WiringFactory indicated it provides a data fetcher but then returned null");
            } else {
                dataFetcher = runtimeWiring.getDataFetchersForType(parentTypeName).get(fieldName);
                if (dataFetcher == null) {
                    dataFetcher = runtimeWiring.getDefaultDataFetcherForType(parentTypeName);
                    if (dataFetcher == null) {
                        dataFetcher = wiringFactory.getDefaultDataFetcher(wiringEnvironment);
                        if (dataFetcher == null) {
                            DataFetcherFactory<?> codeRegistryDFF = codeRegistry.getDefaultDataFetcherFactory();
                            if (codeRegistryDFF != null) {
                                // this will use the default of the code registry when its
                                // asked for at runtime
                                return Optional.empty();
                            }
                            dataFetcher = dataFetcherOfLastResort();
                        }
                    }
                }
            }
            dataFetcherFactory = DataFetcherFactories.useDataFetcher(dataFetcher);
        }
        return Optional.of(dataFetcherFactory);
    }

    GraphQLArgument buildArgument(BuildContext buildCtx, InputValueDefinition valueDefinition) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? valueDefinition : null);
        builder.name(valueDefinition.getName());
        builder.description(buildDescription(buildCtx, valueDefinition, valueDefinition.getDescription()));
        builder.deprecate(buildDeprecationReason(valueDefinition.getDirectives()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLInputType inputType = buildInputType(buildCtx, valueDefinition.getType());
        builder.type(inputType);
        Value<?> defaultValue = valueDefinition.getDefaultValue();
        if (defaultValue != null) {
            builder.defaultValueLiteral(defaultValue);
        }

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                valueDefinition.getDirectives(),
                emptyList(),
                ARGUMENT_DEFINITION,
                buildCtx.getDirectives(),
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        return directivesObserve(buildCtx, builder.build());
    }

    void buildOperations(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
        //
        // Schema can be missing if the type is called 'Query'.  Pre flight checks have checked that!
        //
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        Map<String, OperationTypeDefinition> operationTypeDefs = buildCtx.operationTypeDefs;

        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;

        Optional<OperationTypeDefinition> queryOperation = getOperationNamed("query", operationTypeDefs);
        if (queryOperation.isEmpty()) {
            TypeDefinition<?> queryTypeDef = Objects.requireNonNull(typeRegistry.getTypeOrNull("Query"));
            query = buildOutputType(buildCtx, TypeName.newTypeName().name(queryTypeDef.getName()).build());
        } else {
            query = buildOperation(buildCtx, queryOperation.get());
        }
        schemaBuilder.query(query);

        Optional<OperationTypeDefinition> mutationOperation = getOperationNamed("mutation", operationTypeDefs);
        if (mutationOperation.isEmpty()) {
            if (typeRegistry.schemaDefinition().isEmpty()) {
                // If no schema definition, then there is no schema keyword. Default to using type called Mutation
                TypeDefinition<?> mutationTypeDef = typeRegistry.getTypeOrNull("Mutation");
                if (mutationTypeDef != null) {
                    mutation = buildOutputType(buildCtx, TypeName.newTypeName().name(mutationTypeDef.getName()).build());
                    schemaBuilder.mutation(mutation);
                }
            }
        } else {
            mutation = buildOperation(buildCtx, mutationOperation.get());
            schemaBuilder.mutation(mutation);
        }

        Optional<OperationTypeDefinition> subscriptionOperation = getOperationNamed("subscription", operationTypeDefs);
        if (subscriptionOperation.isEmpty()) {
            if (typeRegistry.schemaDefinition().isEmpty()) {
                // If no schema definition, then there is no schema keyword. Default to using type called Subscription
                TypeDefinition<?> subscriptionTypeDef = typeRegistry.getTypeOrNull("Subscription");
                if (subscriptionTypeDef != null) {
                    subscription = buildOutputType(buildCtx, TypeName.newTypeName().name(subscriptionTypeDef.getName()).build());
                    schemaBuilder.subscription(subscription);
                }
            }
        } else {
            subscription = buildOperation(buildCtx, subscriptionOperation.get());
            schemaBuilder.subscription(subscription);
        }
    }

    void buildSchemaDirectivesAndExtensions(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        List<Directive> schemaDirectiveList = SchemaExtensionsChecker.gatherSchemaDirectives(typeRegistry);
        Set<GraphQLDirective> runtimeDirectives = buildCtx.getDirectives();
        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory(buildCtx),
                schemaDirectiveList,
                emptyList(),
                DirectiveLocation.SCHEMA,
                runtimeDirectives,
                buildCtx.getComparatorRegistry());
        schemaBuilder.withSchemaDirectives(appliedDirectives.first);
        schemaBuilder.withSchemaAppliedDirectives(appliedDirectives.second);

        schemaBuilder.definition(buildCtx.isCaptureAstDefinitions() ? typeRegistry.schemaDefinition().orElse(null) : null);
        schemaBuilder.extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? typeRegistry.getSchemaExtensionDefinitions() : emptyList());
    }

    List<InputObjectTypeExtensionDefinition> inputObjectTypeExtensions(InputObjectTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().inputObjectTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<EnumTypeExtensionDefinition> enumTypeExtensions(EnumTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().enumTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<ScalarTypeExtensionDefinition> scalarTypeExtensions(ScalarTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().scalarTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<InterfaceTypeExtensionDefinition> interfaceTypeExtensions(InterfaceTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().interfaceTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<ObjectTypeExtensionDefinition> objectTypeExtensions(ObjectTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().objectTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<UnionTypeExtensionDefinition> unionTypeExtensions(UnionTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().unionTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    /**
     * We build the query / mutation / subscription path as a tree of referenced types
     * but then we build the rest of the types specified and put them in as additional types
     *
     * @param buildCtx the context we need to work out what we are doing
     *
     * @return the additional types not referenced from the top level operations
     */
    Set<GraphQLNamedType> buildAdditionalTypes(BuildContext buildCtx) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();

        Set<String> detachedTypeNames = getDetachedTypeNames(buildCtx);

        Set<GraphQLNamedType> additionalTypes = new LinkedHashSet<>();
        // recursively record detached types on the ctx and add them to the additionalTypes set
        typeRegistry.types().values().stream()
                .filter(typeDefinition -> detachedTypeNames.contains(typeDefinition.getName()))
                .forEach(typeDefinition -> {
                    TypeName typeName = TypeName.newTypeName().name(typeDefinition.getName()).build();

                    if (typeDefinition instanceof InputObjectTypeDefinition) {
                        if (buildCtx.hasInputType(typeDefinition) == null) {
                            buildCtx.putInputType((GraphQLNamedInputType) buildInputType(buildCtx, typeName));
                        }
                        additionalTypes.add(buildCtx.inputGTypes.get(typeDefinition.getName()));
                    } else {
                        if (buildCtx.hasOutputType(typeDefinition) == null) {
                            buildCtx.putOutputType(buildOutputType(buildCtx, typeName));
                        }
                        additionalTypes.add(buildCtx.outputGTypes.get(typeDefinition.getName()));
                    }
                });

        typeRegistry.scalars().values().stream()
                .filter(typeDefinition -> detachedTypeNames.contains(typeDefinition.getName()))
                .forEach(scalarTypeDefinition -> {
                    if (ScalarInfo.isGraphqlSpecifiedScalar(scalarTypeDefinition.getName())) {
                        return;
                    }

                    if (buildCtx.hasInputType(scalarTypeDefinition) == null && buildCtx.hasOutputType(scalarTypeDefinition) == null) {
                        buildCtx.putOutputType(buildScalar(buildCtx, scalarTypeDefinition));
                    }
                    if (buildCtx.hasInputType(scalarTypeDefinition) != null) {
                        additionalTypes.add(buildCtx.inputGTypes.get(scalarTypeDefinition.getName()));
                    } else if (buildCtx.hasOutputType(scalarTypeDefinition) != null) {
                        additionalTypes.add(buildCtx.outputGTypes.get(scalarTypeDefinition.getName()));
                    }
                });

        return additionalTypes;
    }

    /**
     * Detached types (or additional types) are all types that
     * are not connected to the root operations types.
     *
     * @param buildCtx buildCtx
     *
     * @return detached type names
     */
    private Set<String> getDetachedTypeNames(BuildContext buildCtx) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        // connected types are all types that have a path that connects them back to the root operation types.
        Set<String> connectedTypes = new HashSet<>(buildCtx.inputGTypes.keySet());
        connectedTypes.addAll(buildCtx.outputGTypes.keySet());

        Set<String> allTypeNames = new HashSet<>(typeRegistry.types().keySet());
        Set<String> scalars = new HashSet<>(typeRegistry.scalars().keySet());
        allTypeNames.addAll(scalars);

        // detached types are all types minus the connected types.
        Set<String> detachedTypeNames = new HashSet<>(allTypeNames);
        detachedTypeNames.removeAll(connectedTypes);
        return detachedTypeNames;
    }

    Set<GraphQLDirective> buildAdditionalDirectiveDefinitions(BuildContext buildCtx) {
        Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();

        for (DirectiveDefinition directiveDefinition : typeRegistry.getDirectiveDefinitions().values()) {
            if (IncludeDirective.getName().equals(directiveDefinition.getName())
                    || SkipDirective.getName().equals(directiveDefinition.getName())) {
                // skip and include directives are added by default to the GraphQLSchema via the GraphQLSchema builder.
                continue;
            }
            GraphQLDirective directive = buildDirectiveDefinitionFromAst(buildCtx, directiveDefinition, inputTypeFactory(buildCtx));
            buildCtx.addDirectiveDefinition(directive);
            additionalDirectives.add(directive);
        }
        return additionalDirectives;
    }

    void addDirectivesIncludedByDefault(TypeDefinitionRegistry typeRegistry) {
        // we synthesize this into the type registry - no need for them to add it
        typeRegistry.add(DEPRECATED_DIRECTIVE_DEFINITION);
        typeRegistry.add(SPECIFIED_BY_DIRECTIVE_DEFINITION);
        typeRegistry.add(ONE_OF_DIRECTIVE_DEFINITION);
    }

    private Optional<OperationTypeDefinition> getOperationNamed(String name, Map<String, OperationTypeDefinition> operationTypeDefs) {
        return Optional.ofNullable(operationTypeDefs.get(name));
    }

    private DataFetcher<?> dataFetcherOfLastResort() {
        return SingletonPropertyDataFetcher.singleton();
    }

    private List<Directive> directivesOf(List<? extends TypeDefinition<?>> typeDefinitions) {
        return typeDefinitions.stream()
                .map(TypeDefinition::getDirectives)
                .filter(Objects::nonNull)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    private <T extends GraphQLDirectiveContainer> T directivesObserve(BuildContext buildCtx, T directiveContainer) {
        if (!buildCtx.directiveWiringRequired) {
            boolean requiresWiring = SchemaGeneratorDirectiveHelper.schemaDirectiveWiringIsRequired(directiveContainer, buildCtx.getTypeRegistry(), buildCtx.getWiring());
            buildCtx.directiveWiringRequired = buildCtx.directiveWiringRequired || requiresWiring;
        }
        return directiveContainer;
    }

}
