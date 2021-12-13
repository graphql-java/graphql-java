package graphql.execution.directives;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.execution.MergedField;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLAppliedDirective;
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
    private volatile ImmutableMap<Field, List<GraphQLDirective>> fieldDirectivesByField;
    private volatile ImmutableMap<String, List<GraphQLDirective>> fieldDirectivesByName;
    private volatile ImmutableMap<Field, List<GraphQLAppliedDirective>> fieldAppliedDirectivesByField;
    private volatile ImmutableMap<String, List<GraphQLAppliedDirective>> fieldAppliedDirectivesByName;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> variables) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.variables = variables;
    }

    private void computeValuesLazily() {
        synchronized (this) {
            if (fieldDirectivesByField != null) {
                return;
            }

            final Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            final Map<Field, List<GraphQLAppliedDirective>> byFieldApplied = new LinkedHashMap<>();
            mergedField.getFields().forEach(field -> {
                List<Directive> directives = field.getDirectives();
                ImmutableList<GraphQLDirective> resolvedDirectives = ImmutableList.copyOf(
                        directivesResolver
                                .resolveDirectives(directives, schema, variables)
                                .values()
                );
                byField.put(field, resolvedDirectives);
                // at some point we will only use applied
                byFieldApplied.put(field, ImmutableKit.map(resolvedDirectives, GraphQLDirective::toAppliedDirective));
            });

            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            Map<String, List<GraphQLAppliedDirective>> byNameApplied = new LinkedHashMap<>();
            byField.forEach((field, directiveList) -> directiveList.forEach(directive -> {
                String name = directive.getName();
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(directive);
                // at some point we will only use applied
                byNameApplied.computeIfAbsent(name, k -> new ArrayList<>()).add(directive.toAppliedDirective());
            }));

            this.fieldDirectivesByName = ImmutableMap.copyOf(byName);
            this.fieldDirectivesByField = ImmutableMap.copyOf(byField);
            this.fieldAppliedDirectivesByName = ImmutableMap.copyOf(byNameApplied);
            this.fieldAppliedDirectivesByField = ImmutableMap.copyOf(byFieldApplied);
        }
    }


    @Override
    public Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField() {
        computeValuesLazily();
        return fieldDirectivesByField;
    }

    @Override
    public Map<Field, List<GraphQLAppliedDirective>> getImmediateAppliedDirectivesByField() {
        computeValuesLazily();
        return fieldAppliedDirectivesByField;
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectivesByName() {
        computeValuesLazily();
        return fieldDirectivesByName;
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getImmediateAppliedDirectivesByName() {
        computeValuesLazily();
        return fieldAppliedDirectivesByName;
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateDirectivesByName().getOrDefault(directiveName, emptyList());
    }

    @Override
    public List<GraphQLAppliedDirective> getImmediateAppliedDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateAppliedDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
