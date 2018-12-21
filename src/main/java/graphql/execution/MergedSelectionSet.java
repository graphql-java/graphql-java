package graphql.execution;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@PublicApi
public class MergedSelectionSet {

    private final Map<String, MergedFields> subFields;

    private MergedSelectionSet(Map<String, MergedFields> subFields) {
        this.subFields = subFields;
    }

    public Map<String, MergedFields> getSubFields() {
        return subFields;
    }

    public int size() {
        return subFields.size();
    }

    public Set<String> keySet() {
        return subFields.keySet();
    }

    public MergedFields getSubField(String key) {
        return subFields.get(key);
    }

    public List<String> getKeys() {
        return new ArrayList<>(keySet());
    }

    public static Builder newMergedSelectionSet() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, MergedFields> subFields = new LinkedHashMap<>();

        private Builder() {

        }

        public Builder subFields(Map<String, MergedFields> subFields) {
            this.subFields = subFields;
            return this;
        }

        public MergedSelectionSet build() {
            return new MergedSelectionSet(subFields);
        }

    }

}
