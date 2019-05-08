package graphql.execution.directives;

import graphql.Internal;
import graphql.execution.MergedField;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * These objects are ALWAYS in the context of a single MergedField
 *
 * Also note we compute these values lazily
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();
    private final MergedField mergedField;
    private final GraphQLSchema schema;
    private final Map<String, Object> variables;
    private volatile Map<Field, List<GraphQLDirective>> fieldDirectivesMap;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> variables) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.variables = variables;
    }

    private void computeValuesLazily() {
        synchronized (this) {
            if (fieldDirectivesMap != null) {
                return;
            }

            this.fieldDirectivesMap = new LinkedHashMap<>();
            mergedField.getFields().forEach(field -> {
                List<Directive> directives = field.getDirectives();
                List<GraphQLDirective> resolvedDirectives = new ArrayList<>(
                        directivesResolver
                                .resolveDirectives(directives, schema, variables)
                                .values()
                );
                fieldDirectivesMap.put(field, resolvedDirectives);
            });
        }
    }


    @Override
    public Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField() {
        computeValuesLazily();
        return new LinkedHashMap<>(fieldDirectivesMap);
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectives() {
        computeValuesLazily();
        Map<String, List<GraphQLDirective>> mapOfDirectives = new LinkedHashMap<>();
        fieldDirectivesMap.forEach((field, directiveList) -> {
            directiveList.forEach(directive -> {
                String name = directive.getName();
                mapOfDirectives.computeIfAbsent(name, k -> new ArrayList<>());
                mapOfDirectives.get(name).add(directive);
            });
        });
        return mapOfDirectives;
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateDirectives().getOrDefault(directiveName, emptyList());
    }
}
