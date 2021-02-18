package graphql.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.PublicApi;
import graphql.language.Field;

import java.util.List;
import java.util.Map;
import java.util.Set;

@PublicApi
public class MergedSelectionSet {

    private final Map<String, MergedField> subFields;

    private MergedSelectionSet(Map<String, MergedField> subFields) {
        this.subFields = Assert.assertNotNull(subFields);
    }

    public Map<String, MergedField> getSubFields() {
        return subFields;
    }

    public List<MergedField> getSubFieldsList() {
        return ImmutableList.copyOf(subFields.values());
    }

    public int size() {
        return subFields.size();
    }

    public Set<String> keySet() {
        return subFields.keySet();
    }

    public MergedField getSubField(String key) {
        return subFields.get(key);
    }

    public List<String> getKeys() {
        return ImmutableList.copyOf(keySet());
    }

    public boolean isEmpty() {
        return subFields.isEmpty();
    }

    public static Builder newMergedSelectionSet() {
        return new Builder();
    }

    public static class Builder {

        private final ImmutableMap.Builder<String, MergedField> subFields = ImmutableMap.builder();

        private Builder() {
        }

        public Builder subFields(Map<String, MergedField> subFields) {
            this.subFields.putAll(subFields);
            return this;
        }

        public Builder withSubFields(Map<String, ImmutableList.Builder<Field>> subFields) {
            for (Map.Entry<String, ImmutableList.Builder<Field>> entry : subFields.entrySet()) {
                this.subFields.put(entry.getKey(), MergedField.newMergedField(entry.getValue()).build());
            }
            return this;
        }

        public MergedSelectionSet build() {
            return new MergedSelectionSet(subFields.build());
        }

    }

}
