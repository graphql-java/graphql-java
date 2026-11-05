package graphql.schema.idl;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.Value;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphqlDirectivesContainerTypeBuilder;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.util.Pair;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static graphql.Directives.NO_LONGER_SUPPORTED;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.map;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.schema.idl.SchemaGeneratorHelper.buildDescription;
import static graphql.util.Pair.pair;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * This contains helper code to build out applied directives on schema element
 */
@Internal
class SchemaGeneratorAppliedDirectiveHelper {

    static void buildAppliedDirectives(SchemaGeneratorHelper.BuildContext buildCtx, GraphqlDirectivesContainerTypeBuilder<?, ?> builder, Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives) {
        builder.clearDirectives();

        // for legacy reasons we can use the old directives construct.  This is false by default
        if (!buildCtx.options.isUseAppliedDirectivesOnly()) {
            for (GraphQLDirective directive : appliedDirectives.first) {
                builder.withDirective(directive);
            }
        }
        for (GraphQLAppliedDirective appliedDirective : appliedDirectives.second) {
            builder.withAppliedDirective(appliedDirective);
        }
    }

    static Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> buildAppliedDirectives(
            SchemaGeneratorHelper.BuildContext buildCtx,
            Function<Type<?>, GraphQLInputType> inputTypeFactory,
            List<Directive> directives,
            List<Directive> extensionDirectives,
            Introspection.DirectiveLocation directiveLocation,
            GraphqlTypeComparatorRegistry comparatorRegistry) {
        directives = directives != null ? directives : emptyList();
        extensionDirectives = extensionDirectives != null ? extensionDirectives : emptyList();
        if (directives.isEmpty() && extensionDirectives.isEmpty()) {
            return pair(emptyList(), emptyList());
        }

        List<GraphQLDirective> output = new ArrayList<>();
        List<GraphQLAppliedDirective> outputApplied = new ArrayList<>();
        for (Directive directive : directives) {
            Pair<GraphQLDirective, GraphQLAppliedDirective> pair = buildAppliedDirective(buildCtx,
                    inputTypeFactory,
                    directive,
                    directiveLocation,
                    comparatorRegistry);
            output.add(pair.first);
            outputApplied.add(pair.second);
        }
        for (Directive directive : extensionDirectives) {
            Pair<GraphQLDirective, GraphQLAppliedDirective> pair = buildAppliedDirective(buildCtx,
                    inputTypeFactory,
                    directive,
                    directiveLocation,
                    comparatorRegistry);
            output.add(pair.first);
            outputApplied.add(pair.second);
        }
        return pair(output, outputApplied);
    }

    // builds directives from a type and its extensions
    private static Pair<GraphQLDirective, GraphQLAppliedDirective> buildAppliedDirective(SchemaGeneratorHelper.BuildContext buildCtx,
                                                                                         Function<Type<?>, GraphQLInputType> inputTypeFactory,
                                                                                         Directive directive,
                                                                                         Introspection.DirectiveLocation directiveLocation,
                                                                                         GraphqlTypeComparatorRegistry comparatorRegistry) {
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(buildCtx, directive, null))
                .comparatorRegistry(comparatorRegistry)
                .validLocations(directiveLocation);

        GraphQLAppliedDirective.Builder builderAppliedDirective = GraphQLAppliedDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(buildCtx, directive, null))
                .comparatorRegistry(comparatorRegistry);

        GraphQLDirective graphQLDirective = buildCtx.getDirectiveDefinition(directive.getName());
        if (graphQLDirective == null) {
            graphQLDirective = buildDirectiveDefinitionFromAst(buildCtx,
                    buildCtx.getTypeRegistry().getDirectiveDefinition(directive.getName()).get(), inputTypeFactory);
        }
        builder.repeatable(graphQLDirective.isRepeatable());

        builder.definition(buildCtx.isCaptureAstDefinitions() ? graphQLDirective.getDefinition() : null);
        builderAppliedDirective.definition(buildCtx.isCaptureAstDefinitions() ? directive : null);

        Set<String> providedArguments = new LinkedHashSet<>();
        for (Argument arg : directive.getArguments()) {
            providedArguments.add(arg.getName());
            builder.argument(buildDirectiveArg(buildCtx, arg, graphQLDirective));
            builderAppliedDirective.argument(buildAppliedArg(buildCtx, arg, graphQLDirective));
        }
        addMissingArguments(buildCtx, providedArguments, graphQLDirective, builder, builderAppliedDirective);

        return pair(builder.build(), builderAppliedDirective.build());
    }

    private static GraphQLArgument buildDirectiveArg(SchemaGeneratorHelper.BuildContext buildCtx, Argument arg, GraphQLDirective directiveDefinition) {
        GraphQLArgument directiveDefArgument = directiveDefinition.getArgument(arg.getName());
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        GraphQLInputType inputType = directiveDefArgument.getType();
        builder.name(arg.getName())
                .type(inputType)
                .definition(buildCtx.isCaptureAstDefinitions() ? directiveDefArgument.getDefinition() : null);

        // we know it is a literal because it was created by SchemaGenerator
        if (directiveDefArgument.getArgumentDefaultValue().isSet()) {
            builder.defaultValueLiteral((Value<?>) directiveDefArgument.getArgumentDefaultValue().getValue());
        }

        // Object value = buildCtx, arg.getValue(), inputType);
        // we put the default value in if the specified is null
        if (arg.getValue() != null) {
            //TODO: maybe validation of it
            builder.valueLiteral(arg.getValue());
        }

        return builder.build();
    }

    private static GraphQLAppliedDirectiveArgument buildAppliedArg(SchemaGeneratorHelper.BuildContext buildCtx, Argument arg, GraphQLDirective directiveDefinition) {
        GraphQLArgument directiveDefArgument = directiveDefinition.getArgument(arg.getName());
        GraphQLAppliedDirectiveArgument.Builder builder = GraphQLAppliedDirectiveArgument.newArgument();
        builder.name(arg.getName())
                .type(directiveDefArgument.getType())
                .definition(buildCtx.isCaptureAstDefinitions() ? arg : null);

        // Object value = buildCtx, arg.getValue(), inputType);
        // we put the default value in if the specified is null
        if (arg.getValue() != null) {
            builder.valueLiteral(arg.getValue());
        } else {
            // we know it is a literal because it was created by SchemaGenerator
            if (directiveDefArgument.getArgumentDefaultValue().isSet()) {
                builder.valueLiteral((Value<?>) directiveDefArgument.getArgumentDefaultValue().getValue());
            }
        }
        return builder.build();
    }

    private static void addMissingArguments(SchemaGeneratorHelper.BuildContext buildCtx,
                                            Set<String> providedArguments,
                                            GraphQLDirective directiveDefinition,
                                            GraphQLDirective.Builder builder,
                                            GraphQLAppliedDirective.Builder appliedBuilder) {
        for (GraphQLArgument definitionArgument : directiveDefinition.getArguments()) {
            if (providedArguments.contains(definitionArgument.getName())) {
                continue;
            }
            builder.argument(buildMissingArgument(buildCtx, definitionArgument));
            appliedBuilder.argument(buildMissingAppliedArgument(definitionArgument));
        }
    }

    private static GraphQLArgument buildMissingArgument(SchemaGeneratorHelper.BuildContext buildCtx, GraphQLArgument definitionArgument) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(definitionArgument.getName())
                .description(definitionArgument.getDescription())
                .definition(buildCtx.isCaptureAstDefinitions() ? definitionArgument.getDefinition() : null)
                .type(definitionArgument.getType());
        if (definitionArgument.hasSetDefaultValue()) {
            builder.defaultValueLiteral((Value<?>) definitionArgument.getArgumentDefaultValue().getValue());
        }
        if (definitionArgument.hasSetValue()) {
            builder.valueLiteral((Value<?>) definitionArgument.getArgumentValue().getValue());
        }
        return builder.build();
    }

    private static GraphQLAppliedDirectiveArgument buildMissingAppliedArgument(GraphQLArgument definitionArgument) {
        GraphQLAppliedDirectiveArgument.Builder builder = GraphQLAppliedDirectiveArgument.newArgument()
                .name(definitionArgument.getName())
                .type(definitionArgument.getType())
                .description(definitionArgument.getDescription());
        if (definitionArgument.hasSetDefaultValue()) {
            builder.valueLiteral((Value<?>) definitionArgument.getArgumentDefaultValue().getValue());
        }
        if (definitionArgument.hasSetValue()) {
            builder.valueLiteral((Value<?>) definitionArgument.getArgumentValue().getValue());
        }
        return builder.build();
    }

    static GraphQLDirective buildDirectiveDefinitionFromAst(SchemaGeneratorHelper.BuildContext buildCtx, DirectiveDefinition directiveDefinition, Function<Type<?>, GraphQLInputType> inputTypeFactory) {
        List<DirectiveExtensionDefinition> extensions = buildCtx.getTypeRegistry()
                .directiveExtensions()
                .getOrDefault(directiveDefinition.getName(), emptyList());
        List<Directive> extensionDirectives = extensions.stream()
                .flatMap(extension -> extension.getDirectives().stream())
                .collect(toList());

        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directiveDefinition.getName())
                .definition(buildCtx.isCaptureAstDefinitions() ? directiveDefinition : null)
                .extensionDefinitions(buildCtx.isCaptureAstDefinitions() ? extensions : emptyList())
                .repeatable(directiveDefinition.isRepeatable())
                .deprecate(buildDeprecationReason(combineDirectives(directiveDefinition.getDirectives(), extensionDirectives)))
                .comparatorRegistry(buildCtx.getComparatorRegistry())
                .description(buildDescription(buildCtx, directiveDefinition, directiveDefinition.getDescription()));


        List<Introspection.DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        List<GraphQLArgument> arguments = map(directiveDefinition.getInputValueDefinitions(),
                arg -> buildDirectiveArgumentDefinitionFromAst(buildCtx, arg, inputTypeFactory));
        arguments.forEach(builder::argument);

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory,
                directiveDefinition.getDirectives(),
                extensionDirectives,
                Introspection.DirectiveLocation.DIRECTIVE_DEFINITION,
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);
        return builder.build();
    }

    private static List<Directive> combineDirectives(List<Directive> directives, List<Directive> extensionDirectives) {
        List<Directive> combined = new ArrayList<>(directives);
        combined.addAll(extensionDirectives);
        return combined;
    }

    private static List<Introspection.DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return map(directiveDefinition.getDirectiveLocations(),
                dl -> Introspection.DirectiveLocation.valueOf(dl.getName().toUpperCase(Locale.ROOT)));
    }

    static GraphQLArgument buildDirectiveArgumentDefinitionFromAst(SchemaGeneratorHelper.BuildContext buildCtx, InputValueDefinition valueDefinition, Function<Type<?>, GraphQLInputType> inputTypeFactory) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.definition(buildCtx.isCaptureAstDefinitions() ? valueDefinition : null);
        builder.name(valueDefinition.getName());
        builder.description(buildDescription(buildCtx, valueDefinition, valueDefinition.getDescription()));
        builder.deprecate(buildDeprecationReason(valueDefinition.getDirectives()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLInputType inputType = inputTypeFactory.apply(valueDefinition.getType());
        builder.type(inputType);
        if (valueDefinition.getDefaultValue() != null) {
            builder.valueLiteral(valueDefinition.getDefaultValue());
            builder.defaultValueLiteral(valueDefinition.getDefaultValue());
        }

        Pair<List<GraphQLDirective>, List<GraphQLAppliedDirective>> appliedDirectives = buildAppliedDirectives(
                buildCtx,
                inputTypeFactory,
                valueDefinition.getDirectives(),
                emptyList(),
                ARGUMENT_DEFINITION,
                buildCtx.getComparatorRegistry());
        buildAppliedDirectives(buildCtx, builder, appliedDirectives);

        return builder.build();
    }


    static String buildDeprecationReason(List<Directive> directives) {
        directives = Optional.ofNullable(directives).orElse(emptyList());
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

}
