package graphql.execution.nextgen.depgraph;

import graphql.language.Argument;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;

public class MergedFieldWTC {

    private final List<Field> fields;
    private final List<String> typeConditions;

    private MergedFieldWTC(List<Field> fields, List<String> typeConditions) {
        assertNotEmpty(fields);
        this.fields = new ArrayList<>(fields);
        this.typeConditions = new ArrayList<>(typeConditions);
    }

    /**
     * All merged fields have the same name.
     *
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of of the merged fields.
     */
    public String getName() {
        return fields.get(0).getName();
    }

    /**
     * Returns the key of this MergedFieldWTC for the overall result.
     * This is either an alias or the FieldWTC name.
     *
     * @return the key for this MergedFieldWTC.
     */
    public String getResultKey() {
        Field singleField = getSingleField();
        if (singleField.getAlias() != null) {
            return singleField.getAlias();
        }
        return singleField.getName();
    }

    /**
     * The first of the merged fields.
     *
     * Because all fields are almost identically
     * often only one of the merged fields are used.
     *
     * @return the fist of the merged Fields
     */
    public Field getSingleField() {
        return fields.get(0);
    }

    /**
     * All merged fields share the same arguments.
     *
     * @return the list of arguments
     */
    public List<Argument> getArguments() {
        return getSingleField().getArguments();
    }


    public List<Field> getFields() {
        return new ArrayList<>(fields);
    }

    public static Builder newMergedFieldWTC() {
        return new Builder();
    }

    public static Builder newMergedFieldWTC(Field field) {
        return new Builder().addField(field);
    }

    public static Builder newMergedFieldWTC(List<Field> fields) {
        return new Builder().fields(fields);
    }

    public MergedFieldWTC transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {
        private List<Field> fields = new ArrayList<>();
        private List<String> typeConditions = new ArrayList<>();

        private Builder() {

        }

        private Builder(MergedFieldWTC existing) {
            this.fields = existing.getFields();
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder typeConditions(List<String> typeConditions) {
            this.typeConditions = new ArrayList<>(typeConditions);
            return this;
        }

        public Builder typeCondition(String typeCondition) {
            this.typeConditions.add(typeCondition);
            return this;
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public MergedFieldWTC build() {
            return new MergedFieldWTC(fields, typeConditions);
        }


    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergedFieldWTC that = (MergedFieldWTC) o;
        return fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return "MergedFieldWTC{" +
                "fields=" + fields +
                '}';
    }
}
