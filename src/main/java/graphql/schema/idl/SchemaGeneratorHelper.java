package graphql.schema.idl;

import graphql.Internal;
import graphql.Scalars;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
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
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

/**
 * Simple helper methods with no BuildContext argument
 */
@Internal
public class SchemaGeneratorHelper {

    public Object buildValue(Value value, GraphQLType requiredType) {
        Object result = null;
        if (requiredType instanceof GraphQLNonNull) {
            requiredType = ((GraphQLNonNull) requiredType).getWrappedType();
            assertNotNull(value, "A AST value is required to be present for non null type ;" + requiredType.getName() + "'");
        }
        if (value == null) {
            return null;
        }
        if (requiredType instanceof GraphQLScalarType) {
            result = parseLiteral(value, (GraphQLScalarType) requiredType);
        } else if (value instanceof EnumValue && requiredType instanceof GraphQLEnumType) {
            result = ((EnumValue) value).getName();
        } else if (value instanceof ArrayValue && requiredType instanceof GraphQLList) {
            ArrayValue arrayValue = (ArrayValue) value;
            GraphQLType wrappedType = ((GraphQLList) requiredType).getWrappedType();
            result = arrayValue.getValues().stream()
                    .map(item -> this.buildValue(item, wrappedType)).collect(Collectors.toList());
        } else if (value instanceof ObjectValue && requiredType instanceof GraphQLInputObjectType) {
            result = buildObjectValue((ObjectValue) value, (GraphQLInputObjectType) requiredType);
        } else if (value != null && !(value instanceof NullValue)) {
            assertShouldNeverHappen(
                    "cannot build value of %s from %s", requiredType.getName(), String.valueOf(value));
        }
        return result;
    }

    private Object parseLiteral(Value value, GraphQLScalarType requiredType) {
        if (value instanceof NullValue) {
            return null;
        }
        return requiredType.getCoercing().parseLiteral(value);
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
        if (lines.size() == 0) return null;
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
                return "No longer supported"; // default value from spec
            } else {
                // pre flight checks have ensured its valid
                return args.get("reason");
            }
        }
        return null;
    }

    /**
     * We support the basic types as directive types
     *
     * @param value the value to use
     *
     * @return a graphql input type
     */
    public GraphQLInputType buildDirectiveInputType(Value value) {
        if (value instanceof NullValue) {
            return Scalars.GraphQLString;
        }
        if (value instanceof FloatValue) {
            return Scalars.GraphQLFloat;
        }
        if (value instanceof StringValue) {
            return Scalars.GraphQLString;
        }
        if (value instanceof IntValue) {
            return Scalars.GraphQLInt;
        }
        if (value instanceof BooleanValue) {
            return Scalars.GraphQLBoolean;
        }
        return assertShouldNeverHappen("Directive values of type '%s' are not supported yet", value.getClass().getName());
    }

    // builds directives from a type and its extensions
    public GraphQLDirective buildDirective(Directive directive, Set<GraphQLDirective> directiveDefinitions, DirectiveLocation directiveLocation) {
        Optional<GraphQLDirective> directiveDefinition = directiveDefinitions.stream().filter(dd -> dd.getName().equals(directive.getName())).findFirst();
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(directive, null))
                .validLocations(directiveLocation);

        List<GraphQLArgument> arguments = directive.getArguments().stream()
                .map(arg -> buildDirectiveArgument(arg, directiveDefinition))
                .collect(Collectors.toList());

        if (directiveDefinition.isPresent()) {
            arguments = transferMissingArguments(arguments, directiveDefinition.get());
        }
        arguments.forEach(builder::argument);

        return builder.build();
    }

    private GraphQLArgument buildDirectiveArgument(Argument arg, Optional<GraphQLDirective> directiveDefinition) {
        Optional<GraphQLArgument> directiveDefArgument = directiveDefinition.map(dd -> dd.getArgument(arg.getName()));
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.name(arg.getName());
        GraphQLInputType inputType;
        Object defaultValue = null;
        if (directiveDefArgument.isPresent()) {
            inputType = directiveDefArgument.get().getType();
            defaultValue = directiveDefArgument.get().getDefaultValue();
        } else {
            inputType = buildDirectiveInputType(arg.getValue());
        }
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
                .description(buildDescription(directiveDefinition, directiveDefinition.getDescription()));


        List<DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        List<GraphQLArgument> arguments = directiveDefinition.getInputValueDefinitions().stream()
                .map(arg -> buildDirectiveArgumentFromDefinition(arg, inputTypeFactory))
                .collect(Collectors.toList());
        arguments.forEach(builder::argument);
        return builder.build();
    }

    private GraphQLArgument buildDirectiveArgumentFromDefinition(InputValueDefinition arg, Function<Type, GraphQLInputType> inputTypeFactory) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(arg.getName())
                .definition(arg);

        GraphQLInputType inputType = inputTypeFactory.apply(arg.getType());
        builder.type(inputType);
        builder.defaultValue(buildValue(arg.getDefaultValue(), inputType));
        return builder.build();
    }

    private List<DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return directiveDefinition.getDirectiveLocations().stream()
                .map(dl -> DirectiveLocation.valueOf(dl.getName().toUpperCase()))
                .collect(Collectors.toList());
    }

}
