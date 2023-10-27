package graphql.normalized;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Internal
public class FieldCollectorNormalizedQueryParams {
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> coercedVariableValues;
    private final Map<String, NormalizedInputValue> normalizedVariableValues;
    private final GraphQLContext graphQLContext;
    private final Locale locale;

    private final List<PossibleMerger> possibleMergerList = new ArrayList<>();

    public static class PossibleMerger {
        ExecutableNormalizedField parent;
        String resultKey;

        public PossibleMerger(ExecutableNormalizedField parent, String resultKey) {
            this.parent = parent;
            this.resultKey = resultKey;
        }
    }

    public void addPossibleMergers(ExecutableNormalizedField parent, String resultKey) {
        possibleMergerList.add(new PossibleMerger(parent, resultKey));
    }

    public List<PossibleMerger> getPossibleMergerList() {
        return possibleMergerList;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    @NotNull
    public Map<String, Object> getCoercedVariableValues() {
        return coercedVariableValues;
    }

    @Nullable
    public Map<String, NormalizedInputValue> getNormalizedVariableValues() {
        return normalizedVariableValues;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    public Locale getLocale() {
        return locale;
    }

    private FieldCollectorNormalizedQueryParams(Builder builder) {
        this.fragmentsByName = builder.fragmentsByName;
        this.graphQLSchema = builder.graphQLSchema;
        this.coercedVariableValues = builder.coercedVariableValues;
        this.normalizedVariableValues = builder.normalizedVariableValues;
        this.graphQLContext = builder.graphQLContext;
        this.locale = builder.locale;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private final Map<String, Object> coercedVariableValues = new LinkedHashMap<>();
        private Map<String, NormalizedInputValue> normalizedVariableValues;
        private GraphQLContext graphQLContext = GraphQLContext.getDefault();
        private Locale locale = Locale.getDefault();

        /**
         * @see FieldCollectorNormalizedQueryParams#newParameters()
         */
        private Builder() {

        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName.putAll(fragmentsByName);
            return this;
        }

        public Builder coercedVariables(Map<String, Object> coercedVariableValues) {
            this.coercedVariableValues.putAll(coercedVariableValues);
            return this;
        }

        public Builder normalizedVariables(Map<String, NormalizedInputValue> normalizedVariableValues) {
            this.normalizedVariableValues = normalizedVariableValues;
            return this;
        }

        public Builder graphQLContext(GraphQLContext graphQLContext) {
            this.graphQLContext = graphQLContext;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public FieldCollectorNormalizedQueryParams build() {
            Assert.assertNotNull(graphQLSchema, () -> "You must provide a schema");
            return new FieldCollectorNormalizedQueryParams(this);
        }

    }
}
