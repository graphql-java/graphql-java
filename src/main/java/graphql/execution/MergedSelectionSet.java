package graphql.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.Map;
import java.util.Set;


@PublicApi
@NullMarked
public class MergedSelectionSet {

    private final Map<String, MergedField> subFields;
    private final List<String> keys;

    protected MergedSelectionSet(Map<String, MergedField> subFields) {
        this.subFields = subFields == null ? ImmutableMap.of() : subFields;
        this.keys =  ImmutableList.copyOf(this.subFields.keySet());
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

    public @Nullable MergedField getSubField(String key) {
        return subFields.get(key);
    }

    public List<String> getKeys() {
        return keys;
    }

    public boolean isEmpty() {
        return subFields.isEmpty();
    }

    public static Builder newMergedSelectionSet() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder {

        private Map<String, MergedField> subFields;

        private Builder() {
        }

        public Builder subFields(Map<String, MergedField> subFields) {
            this.subFields = subFields;
            return this;
        }

        public MergedSelectionSet build() {
            return new MergedSelectionSet(subFields);
        }

    }

}
