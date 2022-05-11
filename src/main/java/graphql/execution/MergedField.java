package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;

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
public class MergedField {

    private final ImmutableList<Field> fields;
    private final Field singleField;

    private MergedField(ImmutableList<Field> fields) {
        assertNotEmpty(fields);
        this.fields = fields;
        this.singleField = fields.get(0);
    }

    /**
     * All merged fields have the same name.
     *
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of the merged fields.
     */
    public String getName() {
        return singleField.getName();
    }

    /**
     * Returns the key of this MergedField for the overall result.
     * This is either an alias or the field name.
     *
     * @return the key for this MergedField.
     */
    public String getResultKey() {
        return singleField.getResultKey();
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
        return singleField;
    }

    /**
     * All merged fields share the same arguments.
     *
     * @return the list of arguments
     */
    public List<Argument> getArguments() {
        return singleField.getArguments();
    }


    /**
     * All merged fields
     *
     * @return all merged fields
     */
    public List<Field> getFields() {
        return fields;
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

    public MergedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {

        private final ImmutableList.Builder<Field> fields = new ImmutableList.Builder<>();

        private Builder() {
        }

        private Builder(MergedField existing) {
            fields.addAll(existing.getFields());
        }

        public Builder fields(List<Field> fields) {
            this.fields.addAll(fields);
            return this;
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public MergedField build() {
            return new MergedField(fields.build());
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
        MergedField that = (MergedField) o;
        return fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fields);
    }

    @Override
    public String toString() {
        return "MergedField{" +
                "fields=" + fields +
                '}';
    }
}
