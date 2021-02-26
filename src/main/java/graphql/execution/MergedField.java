package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Field;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * This represent all Fields in a query which overlap and are merged into one.
 * This means they all represent the same field actually when the query is executed.
 *
 * Example query with more than one Field merged together:
 *
 * <pre>
 * {@code
 *
 *      query Foo {
 *          bar
 *          ...BarFragment
 *      }
 *
 *      fragment BarFragment on Query {
 *          bar
 *      }
 * }
 * </pre>
 *
 * Another example:
 * <pre>
 * {@code
 *     {
 *          me{fistName}
 *          me{lastName}
 *     }
 * }
 * </pre>
 *
 * Here the me field is merged together including the sub selections.
 *
 * A third example with different directives:
 * <pre>
 * {@code
 *     {
 *          foo @someDirective
 *          foo @anotherDirective
 *     }
 * }
 * </pre>
 * These examples make clear that you need to consider all merged fields together to have the full picture.
 *
 * The actual logic when fields can successfully merged together is implemented in {#graphql.validation.rules.OverlappingFieldsCanBeMerged}
 */
@PublicApi
public abstract class MergedField {

    /**
     * All merged fields have the same name.
     *
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of of the merged fields.
     */
    public abstract String getName();

    /**
     * Returns the key of this MergedField for the overall result.
     * This is either an alias or the field name.
     *
     * @return the key for this MergedField.
     */
    public abstract String getResultKey();

    /**
     * The first of the merged fields.
     *
     * Because all fields are almost identically
     * often only one of the merged fields are used.
     *
     * @return the fist of the merged Fields
     */
    public abstract Field getSingleField();

    /**
     * All merged fields share the same arguments.
     *
     * @return the list of arguments
     */
    public abstract List<Argument> getArguments();


    /**
     * All merged fields
     *
     * @return all merged fields
     */
    public abstract List<Field> getFields();


    public static MergedField newMergedFieldFast(Field field) {
        return new MonoMergedField(field);
    }

    public static Builder newMergedField() {
        return new Builder();
    }

    public static Builder newMergedField(Field field) {
        return new Builder().addField(field);
    }

    public static Builder newMergedField(List<Field> fields) {
        return new Builder().fields(fields);
    }

    public static Builder newMergedField(ImmutableList.Builder<Field> existingBuilder) {
        return new Builder(existingBuilder);
    }

    public MergedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {

        private final ImmutableList.Builder<Field> fields;

        private Builder() {
            this.fields = ImmutableList.builder();
        }

        // taking ownership of existing builder
        private Builder(ImmutableList.Builder<Field> builder) {
            this.fields = builder;
        }

        private Builder(MergedField existing) {
            this.fields = ImmutableList.builder();
            for (Field field : existing.getFields()) {
                this.fields.add(field);
            }
        }

        public Builder fields(List<Field> fields) {
            for (Field field : fields) {
                this.fields.add(field);
            }
            return this;
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public MergedField build() {
            return new MonoMergedField(fields.build().get(0)); // todo: finish
        }

    }

    private static class MonoMergedField extends MergedField {

        private final Field field;

        private MonoMergedField(Field field) {
            this.field = field;
        }

        @Override
        public String getName() {
            return field.getName();
        }

        @Override
        public String getResultKey() {
            return field.getResultKey();
        }

        @Override
        public Field getSingleField() {
            return field;
        }

        @Override
        public List<Argument> getArguments() {
            return field.getArguments();
        }

        @Override
        public List<Field> getFields() {
            return ImmutableList.of(field);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MonoMergedField that = (MonoMergedField) o;
            return field.equals(that.field);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(field);
        }

        @Override
        public String toString() {
            return "MonoMergedField{" +
                "field=" + field +
                '}';
        }

    }

}
