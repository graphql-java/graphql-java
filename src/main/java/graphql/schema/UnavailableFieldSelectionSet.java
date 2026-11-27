package graphql.schema;

import graphql.Internal;

import java.util.List;
import java.util.Map;

@Internal
public final class UnavailableFieldSelectionSet implements DataFetchingFieldSelectionSet {
    public static final UnavailableFieldSelectionSet INSTANCE = new UnavailableFieldSelectionSet();

    private UnavailableFieldSelectionSet() {
    }

    public static IllegalStateException unavailable() {
        return new IllegalStateException("Lookahead requires NormalizedDocument creation. Set NormalizedDocument.ENABLED to true in GraphQLContext before validation, and retain the NormalizedDocument in preparsed cache entries.");
    }

    @Override
    public boolean contains(String pattern) {
        throw unavailable();
    }

    @Override
    public boolean containsAnyOf(String pattern, String... patterns) {
        throw unavailable();
    }

    @Override
    public boolean containsAllOf(String pattern, String... patterns) {
        throw unavailable();
    }

    @Override
    public List<SelectedField> getFields() {
        throw unavailable();
    }

    @Override
    public List<SelectedField> getImmediateFields() {
        throw unavailable();
    }

    @Override
    public List<SelectedField> getFields(String pattern, String... patterns) {
        throw unavailable();
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
        throw unavailable();
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String pattern, String... patterns) {
        throw unavailable();
    }
}
