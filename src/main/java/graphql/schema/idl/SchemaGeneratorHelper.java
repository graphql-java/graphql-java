package graphql.schema.idl;

import graphql.Directives;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumValue;
import graphql.language.InputValueDefinition;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.valueOf;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.NonNullType.newNonNullType;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Simple helper methods with no BuildContext argument
 */
@Internal
public class SchemaGeneratorHelper {

    static final String NO_LONGER_SUPPORTED = "No longer supported";
    static final DirectiveDefinition DEPRECATED_DIRECTIVE_DEFINITION;
    static final DirectiveDefinition SPECIFIED_BY_DIRECTIVE_DEFINITION;

    static {
        DEPRECATED_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.DeprecatedDirective.getName())
                .directiveLocation(newDirectiveLocation().name(FIELD_DEFINITION.name()).build())
                .directiveLocation(newDirectiveLocation().name(ENUM_VALUE.name()).build())
                .description(createDescription("Marks the field or enum value as deprecated"))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("reason")
                                .description(createDescription("The reason for the deprecation"))
                                .type(newTypeName().name("String").build())
                                .defaultValue(StringValue.newStringValue().value(NO_LONGER_SUPPORTED).build())
                                .build())
                .build();

        SPECIFIED_BY_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.SpecifiedByDirective.getName())
                .directiveLocation(newDirectiveLocation().name(SCALAR.name()).build())
                .description(createDescription("Exposes a URL that specifies the behaviour of this scalar."))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("url")
                                .description(createDescription("The URL that specifies the behaviour of this scalar."))
                                .type(newNonNullType(newTypeName().name("String").build()).build())
                                .build())
                .build();
    }

    private static Description createDescription(String s) {
        return new Description(s, null, false);
    }

    public Object buildValue(Value value, GraphQLType requiredType) {
        Object result = null;
        if (GraphQLTypeUtil.isNonNull(requiredType)) {
            requiredType = unwrapOne(requiredType);
        }
        if (value == null || value instanceof NullValue) {
            return null;
        }
        if (requiredType instanceof GraphQLScalarType) {
            result = parseLiteral(value, (GraphQLScalarType) requiredType);
        } else if (requiredType instanceof GraphQLEnumType && value instanceof EnumValue) {
            result = ((EnumValue) value).getName();
        } else if (requiredType instanceof GraphQLEnumType && value instanceof StringValue) {
            result = ((StringValue) value).getValue();
        } else if (isList(requiredType)) {
            if (value instanceof ArrayValue) {
                result = buildArrayValue(requiredType, (ArrayValue) value);
            } else {
                result = buildArrayValue(requiredType, ArrayValue.newArrayValue().value(value).build());
            }
        } else if (value instanceof ObjectValue && requiredType instanceof GraphQLInputObjectType) {
            result = buildObjectValue((ObjectValue) value, (GraphQLInputObjectType) requiredType);
        } else {
            assertShouldNeverHappen(
                    "cannot build value of type %s from object class %s with instance %s", simplePrint(requiredType), value.getClass().getSimpleName(), String.valueOf(value));
        }
        return result;
    }

    private Object parseLiteral(Value value, GraphQLScalarType requiredType) {
        if (value instanceof NullValue) {
            return null;
        }
        return requiredType.getCoercing().parseLiteral(value);
    }

    public Object buildArrayValue(GraphQLType requiredType, ArrayValue arrayValue) {
        Object result;
        GraphQLType wrappedType = unwrapOne(requiredType);
        result = arrayValue.getValues().stream()
                .map(item -> this.buildValue(item, wrappedType)).collect(toList());
        return result;
    }


    public Object buildObjectValue(ObjectValue defaultValue, GraphQLInputObjectType objectType) {
        Map<String, Object> map = new LinkedHashMap<>();
        defaultValue.getObjectFields().forEach(of -> map.put(of.getName(),
                buildValue(of.getValue(), objectType.getField(of.getName()).getType())));
        return map;
    }

    public String buildDescription(Node<?> node, Description description) {
        if (description != null) {
            return description.getContent();
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
        return lines.stream().collect(joining("\n"));
    }

    public String buildDeprecationReason(List<Directive> directives) {
        directives = directives == null ? emptyList() : directives;
        Optional<Directive> directive = directives.stream().filter(d -> "deprecated".equals(d.getName())).findFirst();
        if (directive.isPresent()) {
            Map<String, String> args = directive.get().getArguments().stream().collect(toMap(
                    Argument::getName, arg -> ((StringValue) arg.getValue()).getValue()
            ));
            if (args.isEmpty()) {
                return NO_LONGER_SUPPORTED; // default value from spec
            } else {
                // pre flight checks have ensured its valid
                return args.get("reason");
            }
        }
        return null;
    }

    public void addDirectivesIncludedByDefault(TypeDefinitionRegistry typeRegistry) {
        // we synthesize this into the type registry - no need for them to add it
        typeRegistry.add(DEPRECATED_DIRECTIVE_DEFINITION);
        typeRegistry.add(SPECIFIED_BY_DIRECTIVE_DEFINITION);
    }


    // builds directives from a type and its extensions
    public GraphQLDirective buildDirective(Directive directive, Set<GraphQLDirective> directiveDefinitions, DirectiveLocation directiveLocation, GraphqlTypeComparatorRegistry comparatorRegistry) {
        GraphQLDirective directiveDefinition = FpKit.findOne(directiveDefinitions, dd -> dd.getName().equals(directive.getName())).get();
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(directive, null))
                .comparatorRegistry(comparatorRegistry)
                .validLocations(directiveLocation);

        List<GraphQLArgument> arguments = directive.getArguments().stream()
                .map(arg -> buildDirectiveArgument(arg, directiveDefinition))
                .collect(toList());

        arguments = transferMissingArguments(arguments, directiveDefinition);
        arguments.forEach(builder::argument);

        return builder.build();
    }

    private GraphQLArgument buildDirectiveArgument(Argument arg, GraphQLDirective directiveDefinition) {
        GraphQLArgument directiveDefArgument = directiveDefinition.getArgument(arg.getName());
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.name(arg.getName());
        GraphQLInputType inputType;
        Object defaultValue = null;
        inputType = directiveDefArgument.getType();
        defaultValue = directiveDefArgument.getDefaultValue();
        builder.type(inputType);
        builder.defaultValue(defaultValue);

        Object value = buildValue(arg.getValue(), inputType);
        //
        // we put the default value in if the specified is null
        builder.value(value == null ? defaultValue : value);

        return builder.build();
    }

    private List<GraphQLArgument> transferMissingArguments(List<GraphQLArgument> arguments, GraphQLDirective directiveDefinition) {
        Map<String, GraphQLArgument> declaredArgs = FpKit.getByName(arguments, GraphQLArgument::getName, FpKit.mergeFirst());
        List<GraphQLArgument> argumentsOut = new ArrayList<>(arguments);

        for (GraphQLArgument directiveDefArg : directiveDefinition.getArguments()) {
            if (!declaredArgs.containsKey(directiveDefArg.getName())) {
                GraphQLArgument missingArg = GraphQLArgument.newArgument()
                        .name(directiveDefArg.getName())
                        .description(directiveDefArg.getDescription())
                        .definition(directiveDefArg.getDefinition())
                        .type(directiveDefArg.getType())
                        .defaultValue(directiveDefArg.getDefaultValue())
                        .value(directiveDefArg.getDefaultValue())
                        .build();
                argumentsOut.add(missingArg);
            }
        }
        return argumentsOut;
    }

    public GraphQLDirective buildDirectiveFromDefinition(DirectiveDefinition directiveDefinition, Function<Type, GraphQLInputType> inputTypeFactory) {

        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directiveDefinition.getName())
                .definition(directiveDefinition)
                .description(buildDescription(directiveDefinition, directiveDefinition.getDescription()));


        List<DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        List<GraphQLArgument> arguments = directiveDefinition.getInputValueDefinitions().stream()
                .map(arg -> buildDirectiveArgumentFromDefinition(arg, inputTypeFactory))
                .collect(toList());
        arguments.forEach(builder::argument);
        return builder.build();
    }

    private GraphQLArgument buildDirectiveArgumentFromDefinition(InputValueDefinition arg, Function<Type, GraphQLInputType> inputTypeFactory) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(arg.getName())
                .definition(arg);

        GraphQLInputType inputType = inputTypeFactory.apply(arg.getType());
        builder.type(inputType);
        builder.value(buildValue(arg.getDefaultValue(), inputType));
        builder.defaultValue(buildValue(arg.getDefaultValue(), inputType));
        builder.description(buildDescription(arg, arg.getDescription()));
        return builder.build();
    }

    private List<DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return directiveDefinition.getDirectiveLocations().stream()
                .map(dl -> valueOf(dl.getName().toUpperCase()))
                .collect(toList());
    }

}
