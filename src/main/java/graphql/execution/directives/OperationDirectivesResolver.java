package graphql.execution.directives;

import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Internal
@NullMarked
public class OperationDirectivesResolver {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();

    public Map<OperationDefinition, List<QueryAppliedDirective>> resolveDirectives(Document document, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        Map<OperationDefinition, List<QueryAppliedDirective>> map = new LinkedHashMap<>();
        List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
        for (OperationDefinition operationDefinition : operations) {
            map.put(operationDefinition, resolveDirectives(operationDefinition, schema, variables, graphQLContext, locale));
        }
        return ImmutableMap.copyOf(map);
    }

    public List<QueryAppliedDirective> resolveDirectives(OperationDefinition operationDefinition, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        return directivesResolver.toAppliedDirectives(
                operationDefinition.getDirectives(),
                schema,
                variables,
                graphQLContext,
                locale
        );
    }

    public Map<String, List<QueryAppliedDirective>> resolveDirectivesByName(OperationDefinition operationDefinition, GraphQLSchema schema, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        List<QueryAppliedDirective> list = resolveDirectives(operationDefinition, schema, variables, graphQLContext, locale);
        return toAppliedDirectivesByName(list);
    }

    public static Map<String, List<QueryAppliedDirective>> toAppliedDirectivesByName(List<QueryAppliedDirective> queryAppliedDirectives) {
        Map<String, List<QueryAppliedDirective>> map = new LinkedHashMap<>();
        for (QueryAppliedDirective queryAppliedDirective : queryAppliedDirectives) {
            List<QueryAppliedDirective> list = map.computeIfAbsent(queryAppliedDirective.getName(), k -> new ArrayList<>());
            list.add(queryAppliedDirective);
        }
        return ImmutableMap.copyOf(map);
    }

}
