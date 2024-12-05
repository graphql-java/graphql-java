package graphql.execution.directives;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;
import graphql.util.LockKit;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static graphql.collect.ImmutableKit.emptyList;

/**
 * These objects are ALWAYS in the context of a single MergedField
 * <p>
 * Also note we compute these values lazily
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();
    private final MergedField mergedField;
    private final GraphQLSchema schema;
    private final Map<String, Object> coercedVariables;
    @Nullable
    private final Map<String, NormalizedInputValue> normalizedVariableValues;
    private final GraphQLContext graphQLContext;
    private final Locale locale;

    private final LockKit.ComputedOnce computedOnce = new LockKit.ComputedOnce();
    private volatile ImmutableMap<Field, List<GraphQLDirective>> fieldDirectivesByField;
    private volatile ImmutableMap<String, List<GraphQLDirective>> fieldDirectivesByName;
    private volatile ImmutableMap<Field, List<QueryAppliedDirective>> fieldAppliedDirectivesByField;
    private volatile ImmutableMap<String, List<QueryAppliedDirective>> fieldAppliedDirectivesByName;
    private volatile ImmutableMap<QueryAppliedDirective, Map<String, NormalizedInputValue>> normalizedValuesByAppliedDirective;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> coercedVariables, Map<String, NormalizedInputValue> normalizedVariableValues, GraphQLContext graphQLContext, Locale locale) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.coercedVariables = coercedVariables;
        this.normalizedVariableValues = normalizedVariableValues;
        this.graphQLContext = graphQLContext;
        this.locale = locale;
    }

    private void computeValuesLazily() {
        computedOnce.runOnce(() -> {

            final Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            final Map<Field, List<QueryAppliedDirective>> byFieldApplied = new LinkedHashMap<>();

            BiMap<GraphQLDirective, Directive> directiveCounterParts = HashBiMap.create();
            BiMap<GraphQLDirective, QueryAppliedDirective> gqlDirectiveCounterParts = HashBiMap.create();
            BiMap<QueryAppliedDirective, GraphQLDirective> gqlDirectiveCounterPartsInverse = gqlDirectiveCounterParts.inverse();
            mergedField.getFields().forEach(field -> {
                List<Directive> directives = field.getDirectives();
                BiMap<GraphQLDirective, Directive> directivesMap = directivesResolver
                        .resolveDirectives(directives, schema, coercedVariables, graphQLContext, locale);

                directiveCounterParts.putAll(directivesMap);

                ImmutableList<GraphQLDirective> resolvedDirectives = ImmutableList.copyOf(directivesMap.keySet());

                ImmutableList.Builder<QueryAppliedDirective> appliedDirectiveBuilder = ImmutableList.builder();
                for (GraphQLDirective resolvedDirective : resolvedDirectives) {
                    QueryAppliedDirective appliedDirective = toAppliedDirective(resolvedDirective);
                    appliedDirectiveBuilder.add(appliedDirective);
                    gqlDirectiveCounterParts.put(resolvedDirective, appliedDirective);
                }
                byField.put(field, resolvedDirectives);
                // at some point we will only use applied
                byFieldApplied.put(field, appliedDirectiveBuilder.build());
            });

            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            Map<String, List<QueryAppliedDirective>> byNameApplied = new LinkedHashMap<>();
            byField.forEach((field, directiveList) -> directiveList.forEach(directive -> {
                String name = directive.getName();
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(directive);
                // at some point we will only use applied
                QueryAppliedDirective appliedDirective = gqlDirectiveCounterParts.get(directive);
                byNameApplied.computeIfAbsent(name, k -> new ArrayList<>()).add(appliedDirective);
            }));

            // create NormalizedInputValue values for directive arguments
            Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> normalizedValuesByAppliedDirective = new LinkedHashMap<>();
            if (this.normalizedVariableValues != null) {
                byNameApplied.values().forEach(directiveList -> {
                    for (QueryAppliedDirective queryAppliedDirective : directiveList) {
                        GraphQLDirective graphQLDirective = gqlDirectiveCounterPartsInverse.get(queryAppliedDirective);
                        // we need this counterpart because the ValuesResolver needs the runtime and AST element
                        Directive directive = directiveCounterParts.get(graphQLDirective);
                        if (directive != null) {
                            Map<String, NormalizedInputValue> normalizedArgumentValues = ValuesResolver.getNormalizedArgumentValues(graphQLDirective.getArguments(), directive.getArguments(), this.normalizedVariableValues);
                            normalizedValuesByAppliedDirective.put(queryAppliedDirective, normalizedArgumentValues);
                        }
                    }
                });
            }

            this.fieldDirectivesByName = ImmutableMap.copyOf(byName);
            this.fieldDirectivesByField = ImmutableMap.copyOf(byField);
            this.fieldAppliedDirectivesByName = ImmutableMap.copyOf(byNameApplied);
            this.fieldAppliedDirectivesByField = ImmutableMap.copyOf(byFieldApplied);
            this.normalizedValuesByAppliedDirective = ImmutableMap.copyOf(normalizedValuesByAppliedDirective);
        });
    }

    private QueryAppliedDirective toAppliedDirective(GraphQLDirective directive) {
        QueryAppliedDirective.Builder builder = QueryAppliedDirective.newDirective();
        builder.name(directive.getName());
        for (GraphQLArgument argument : directive.getArguments()) {
            builder.argument(toAppliedArgument(argument));
        }
        return builder.build();
    }

    private QueryAppliedDirectiveArgument toAppliedArgument(GraphQLArgument argument) {
        return QueryAppliedDirectiveArgument.newArgument()
                .name(argument.getName())
                .type(argument.getType())
                .inputValueWithState(argument.getArgumentValue())
                .build();
    }


    @Override
    public Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField() {
        computeValuesLazily();
        return fieldDirectivesByField;
    }

    @Override
    public Map<Field, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByField() {
        computeValuesLazily();
        return fieldAppliedDirectivesByField;
    }

    @Override
    public Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> getNormalizedInputValueByImmediateAppliedDirectives() {
        computeValuesLazily();
        return normalizedValuesByAppliedDirective;
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectivesByName() {
        computeValuesLazily();
        return fieldDirectivesByName;
    }

    @Override
    public Map<String, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByName() {
        computeValuesLazily();
        return fieldAppliedDirectivesByName;
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateDirectivesByName().getOrDefault(directiveName, emptyList());
    }

    @Override
    public List<QueryAppliedDirective> getImmediateAppliedDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateAppliedDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
