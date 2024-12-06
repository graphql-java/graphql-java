package graphql.execution.directives;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This turns AST directives into runtime directives with resolved types and so on
 */
@Internal
public class DirectivesResolver {

    public DirectivesResolver() {
    }

    public BiMap<GraphQLDirective, Directive> resolveDirectives(List<Directive> directives, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry();
        BiMap<GraphQLDirective, Directive> directiveMap = HashBiMap.create();
        directives.forEach(directive -> {
            GraphQLDirective protoType = schema.getDirective(directive.getName());
            if (protoType != null) {
                GraphQLDirective graphQLDirective = protoType.transform(builder -> buildArguments(builder, codeRegistry, protoType, directive, variables, graphQLContext, locale));
                directiveMap.put(graphQLDirective, directive);
            }
        });
        return ImmutableBiMap.copyOf(directiveMap);
    }

    private void buildArguments(GraphQLDirective.Builder directiveBuilder,
                                GraphQLCodeRegistry codeRegistry,
                                GraphQLDirective protoType,
                                Directive fieldDirective,
                                CoercedVariables variables,
                                GraphQLContext graphQLContext,
                                Locale locale) {
        Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(codeRegistry, protoType.getArguments(), fieldDirective.getArguments(), variables, graphQLContext, locale);
        directiveBuilder.clearArguments();
        protoType.getArguments().forEach(protoArg -> {
            if (argumentValues.containsKey(protoArg.getName())) {
                Object argValue = argumentValues.get(protoArg.getName());
                GraphQLArgument newArgument = protoArg.transform(argBuilder -> argBuilder.value(argValue));
                directiveBuilder.argument(newArgument);
            } else {
                // this means they can ask for the argument default value because the argument on the directive
                // object is present - but null
                GraphQLArgument newArgument = protoArg.transform(argBuilder -> argBuilder.value(null));
                directiveBuilder.argument(newArgument);
            }
        });
    }
}
