package graphql.execution.directives;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Internal
@NullMarked
public class OperationDirectivesResolver {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();

    public ImmutableMap<OperationDefinition, ImmutableList<QueryAppliedDirective>> resolveDirectives(Document document, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        ImmutableMap.Builder<OperationDefinition, ImmutableList<QueryAppliedDirective>> builder = ImmutableMap.builder();
        for (OperationDefinition operationDefinition : document.getDefinitionsOfType(OperationDefinition.class)) {
            builder.put(operationDefinition, resolveDirectives(operationDefinition, schema, variables, graphQLContext, locale));
        }
        return builder.build();
    }

    public ImmutableList<QueryAppliedDirective> resolveDirectives(OperationDefinition operationDefinition, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        return directivesResolver.toAppliedDirectives(
                operationDefinition.getDirectives(),
                schema,
                variables,
                graphQLContext,
                locale
        );
    }

    public ImmutableMap<String, ImmutableList<QueryAppliedDirective>> resolveDirectivesByName(OperationDefinition operationDefinition, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        List<QueryAppliedDirective> list = resolveDirectives(operationDefinition, schema, variables, graphQLContext, locale);
        return toAppliedDirectivesByName(list);
    }

    public static ImmutableMap<String, ImmutableList<QueryAppliedDirective>> toAppliedDirectivesByName(List<QueryAppliedDirective> queryAppliedDirectives) {
        Map<String, ImmutableList<QueryAppliedDirective>> immutableListMap = FpKit.groupingBy(queryAppliedDirectives, QueryAppliedDirective::getName);
        return ImmutableMap.copyOf(immutableListMap);
    }

}
