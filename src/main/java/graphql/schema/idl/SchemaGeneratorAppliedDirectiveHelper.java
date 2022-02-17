package graphql.schema.idl;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.Type;
import graphql.language.Value;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphqlDirectivesContainerTypeBuilder;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.util.FpKit;
import graphql.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static graphql.collect.ImmutableKit.map;
import static graphql.schema.idl.SchemaGeneratorHelper.buildDescription;
import static graphql.util.Pair.pair;
import static java.util.Collections.emptyList;

/**
 * This contains helper code to build out appliedm directives on schema element
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
            Set<GraphQLDirective> runtimeDirectives,
            GraphqlTypeComparatorRegistry comparatorRegistry) {
        directives = Optional.ofNullable(directives).orElse(emptyList());
        extensionDirectives = Optional.ofNullable(extensionDirectives).orElse(emptyList());

        List<GraphQLDirective> output = new ArrayList<>();
        List<GraphQLAppliedDirective> outputApplied = new ArrayList<>();
        for (Directive directive : directives) {
            Pair<GraphQLDirective, GraphQLAppliedDirective> pair = buildAppliedDirective(buildCtx,
                    inputTypeFactory,
                    directive,
                    runtimeDirectives,
                    directiveLocation,
                    comparatorRegistry);
            output.add(pair.first);
            outputApplied.add(pair.second);
        }
        for (Directive directive : extensionDirectives) {
            Pair<GraphQLDirective, GraphQLAppliedDirective> pair = buildAppliedDirective(buildCtx,
                    inputTypeFactory,
                    directive,
                    runtimeDirectives,
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
                                                                                         Set<GraphQLDirective> directiveDefinitions,
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

        Optional<GraphQLDirective> directiveDefOpt = FpKit.findOne(directiveDefinitions, dd -> dd.getName().equals(directive.getName()));

        GraphQLDirective graphQLDirective = directiveDefOpt.orElseGet(() -> {
            return buildDirectiveDefinitionFromAst(buildCtx, buildCtx.getTypeRegistry().getDirectiveDefinition(directive.getName()).get(), inputTypeFactory);
        });
        builder.repeatable(graphQLDirective.isRepeatable());

        builder.definition(buildCtx.isCaptureAstDefinitions() ? graphQLDirective.getDefinition() : null);
        builderAppliedDirective.definition(buildCtx.isCaptureAstDefinitions() ? directive : null);

        List<GraphQLArgument> directiveArguments = new ArrayList<>();
        List<GraphQLAppliedDirectiveArgument> appliedArguments = new ArrayList<>();

        for (Argument arg : directive.getArguments()) {
            directiveArguments.add(buildDirectiveArg(buildCtx, arg, graphQLDirective));
            appliedArguments.add(buildAppliedArg(buildCtx, arg, graphQLDirective));
        }

        directiveArguments = transferMissingArguments(buildCtx, directiveArguments, graphQLDirective);
        directiveArguments.forEach(builder::argument);

        appliedArguments = transferMissingAppliedArguments(appliedArguments, graphQLDirective);
        appliedArguments.forEach(builderAppliedDirective::argument);

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

    private static List<GraphQLArgument> transferMissingArguments(SchemaGeneratorHelper.BuildContext buildCtx, List<GraphQLArgument> arguments, GraphQLDirective directiveDefinition) {
        Map<String, GraphQLArgument> declaredArgs = FpKit.getByName(arguments, GraphQLArgument::getName, FpKit.mergeFirst());
        List<GraphQLArgument> argumentsOut = new ArrayList<>(arguments);

        for (GraphQLArgument directiveDefArg : directiveDefinition.getArguments()) {
            if (!declaredArgs.containsKey(directiveDefArg.getName())) {
                GraphQLArgument.Builder missingArg = GraphQLArgument.newArgument()
                        .name(directiveDefArg.getName())
                        .description(directiveDefArg.getDescription())
                        .definition(buildCtx.isCaptureAstDefinitions() ? directiveDefArg.getDefinition() : null)
                        .type(directiveDefArg.getType());

                if (directiveDefArg.hasSetDefaultValue()) {
                    missingArg.defaultValueLiteral((Value<?>) directiveDefArg.getArgumentDefaultValue().getValue());
                }
                if (directiveDefArg.hasSetValue()) {
                    missingArg.valueLiteral((Value<?>) directiveDefArg.getArgumentValue().getValue());
                }
                argumentsOut.add(missingArg.build());
            }
        }
        return argumentsOut;
    }

    private static List<GraphQLAppliedDirectiveArgument> transferMissingAppliedArguments(List<GraphQLAppliedDirectiveArgument> arguments, GraphQLDirective directiveDefinition) {
        Map<String, GraphQLAppliedDirectiveArgument> declaredArgs = FpKit.getByName(arguments, GraphQLAppliedDirectiveArgument::getName, FpKit.mergeFirst());
        List<GraphQLAppliedDirectiveArgument> argumentsOut = new ArrayList<>(arguments);

        for (GraphQLArgument directiveDefArg : directiveDefinition.getArguments()) {
            if (!declaredArgs.containsKey(directiveDefArg.getName())) {
                GraphQLAppliedDirectiveArgument.Builder missingArg = GraphQLAppliedDirectiveArgument.newArgument()
                        .name(directiveDefArg.getName())
                        .type(directiveDefArg.getType())
                        .description(directiveDefArg.getDescription());

                if (directiveDefArg.hasSetDefaultValue()) {
                    missingArg.valueLiteral((Value<?>) directiveDefArg.getArgumentDefaultValue().getValue());
                }
                if (directiveDefArg.hasSetValue()) {
                    missingArg.valueLiteral((Value<?>) directiveDefArg.getArgumentValue().getValue());
                }
                argumentsOut.add(missingArg.build());
            }
        }
        return argumentsOut;
    }

    static GraphQLDirective buildDirectiveDefinitionFromAst(SchemaGeneratorHelper.BuildContext buildCtx, DirectiveDefinition directiveDefinition, Function<Type<?>, GraphQLInputType> inputTypeFactory) {

        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directiveDefinition.getName())
                .definition(buildCtx.isCaptureAstDefinitions() ? directiveDefinition : null)
                .repeatable(directiveDefinition.isRepeatable())
                .description(buildDescription(buildCtx, directiveDefinition, directiveDefinition.getDescription()));


        List<Introspection.DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        List<GraphQLArgument> arguments = map(directiveDefinition.getInputValueDefinitions(),
                arg -> buildDirectiveArgumentDefinitionFromAst(buildCtx, arg, inputTypeFactory));
        arguments.forEach(builder::argument);
        return builder.build();
    }

    private static List<Introspection.DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return map(directiveDefinition.getDirectiveLocations(),
                dl -> Introspection.DirectiveLocation.valueOf(dl.getName().toUpperCase()));
    }

    static GraphQLArgument buildDirectiveArgumentDefinitionFromAst(SchemaGeneratorHelper.BuildContext buildCtx, InputValueDefinition arg, Function<Type<?>, GraphQLInputType> inputTypeFactory) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(arg.getName())
                .definition(buildCtx.isCaptureAstDefinitions() ? arg : null);

        GraphQLInputType inputType = inputTypeFactory.apply(arg.getType());
        builder.type(inputType);
        if (arg.getDefaultValue() != null) {
            builder.valueLiteral(arg.getDefaultValue());
            builder.defaultValueLiteral(arg.getDefaultValue());
        }
        builder.description(buildDescription(buildCtx, arg, arg.getDescription()));
        return builder.build();
    }
}
