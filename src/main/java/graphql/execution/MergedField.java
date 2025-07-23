package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.ExperimentalApi;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.incremental.DeferredExecution;
import graphql.language.Argument;
import graphql.language.Field;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;

/**
 * This represents all Fields in a query which overlap and are merged into one.
 * This means they all represent the same field actually when the query is executed.
 * <p>
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
 * Here the field is merged together including the sub selections.
 * <p>
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
 * <p>
 * The actual logic when fields can be successfully merged together is implemented in {#graphql.validation.rules.OverlappingFieldsCanBeMerged}
 */
@PublicApi
@NullMarked
public class MergedField {

    private @Nullable ImmutableList<Field> fields;
    private final Field singleField;
    private final ImmutableList<DeferredExecution> deferredExecutions;

    private MergedField(ImmutableList<Field> fields, ImmutableList<DeferredExecution> deferredExecutions) {
        assertNotEmpty(fields);
        this.fields = fields;
        this.singleField = fields.get(0);
        this.deferredExecutions = deferredExecutions;
    }

    private MergedField(Field field, ImmutableList<DeferredExecution> deferredExecutions) {
        // we make "this.fields" lazy because mostly we have single fields, and we avoid a
        // list allocation if we can.  This is a micro memory optimisation but for large
        // operations this can add up.
        this.fields = null;
        this.singleField = field;
        this.deferredExecutions = deferredExecutions;
    }

    /**
     * All merged fields have the same name.
     * <p>
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
     * <p>
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
        List<Field> fields = this.fields;
        if (fields == null) {
            synchronized (this) {
                if (this.fields == null) {
                    this.fields = ImmutableList.of(singleField);
                }
                fields = this.fields;
            }
        }
        return fields;
    }

    /**
     * @return how many fields are in this merged field
     */
    public int getFieldsCount() {
        if (this.fields == null) {
            return 1;
        }
        return this.fields.size();
    }

    /**
     * @return true if the field has a sub selection
     */
    public boolean hasSubSelection() {
        if (this.fields == null) {
            return singleField.getSelectionSet() != null;
        }
        for (Field field : this.fields) {
            if (field.getSelectionSet() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if this {@link MergedField} represents a single {@link Field} in the operation
     */
    public boolean isSingleField() {
        if (this.fields == null) {
            return true;
        }
        return this.fields.size() == 1;
    }

    /**
     * Get a list of all {@link DeferredExecution}s that this field is part of
     *
     * @return all defer executions.
     */
    @ExperimentalApi
    public List<DeferredExecution> getDeferredExecutions() {
        return deferredExecutions;
    }

    /**
     * Returns true if this field is part of a deferred execution
     *
     * @return true if this field is part of a deferred execution
     */
    @ExperimentalApi
    public boolean isDeferred() {
        return !deferredExecutions.isEmpty();
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
        if (fields != null) {
            return fields.equals(that.fields);
        } else {
            return this.singleField.equals(that.singleField);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Objects.requireNonNullElse(fields, singleField));
    }

    @Override
    public String toString() {
        return "MergedField{" +
                "field(s)=" + (fields != null ? fields : singleField) +
                '}';
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

    /**
     * This is an important method because it creates a new MergedField from the existing one without using a builder
     * to save memory.
     *
     * @param field             the new field to add to the current merged field
     * @param deferredExecution the deferred execution
     *
     * @return a new {@link MergedField} instance
     */
    MergedField newMergedFieldWith(Field field, @Nullable DeferredExecution deferredExecution) {
        ImmutableList<DeferredExecution> deferredExecutions = this.deferredExecutions;
        if (deferredExecution != null) {
            deferredExecutions = ImmutableKit.addToList(deferredExecutions, deferredExecution);
        }
        ImmutableList<Field> fields = this.fields;
        if (fields == null) {
            fields = ImmutableList.of(singleField, field);
        } else {
            fields = ImmutableKit.addToList(fields, field);
        }
        return new MergedField(fields, deferredExecutions);
    }

    /**
     * This is an important method in that it creates a MergedField direct without the list and without a builder and hence
     * saves some micro memory in not allocating a list of 1
     *
     * @param field             the field to wrap
     * @param deferredExecution the deferred execution
     *
     * @return a new {@link MergedField}
     */
    static MergedField newSingletonMergedField(Field field, @Nullable DeferredExecution deferredExecution) {
        return new MergedField(field, deferredExecution == null ? ImmutableList.of() : ImmutableList.of(deferredExecution));
    }

    public MergedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * Runs a consumer for each field
     *
     * @param fieldConsumer the consumer to run
     */
    public void forEach(Consumer<Field> fieldConsumer) {
        if (fields == null) {
            fieldConsumer.accept(singleField);
        } else {
            fields.forEach(fieldConsumer);
        }
    }

    public static class Builder {

        /*
            The builder logic is complicated by these dual singleton / list duality code,
            but it prevents memory allocation curing field collection and every bit counts
            when the CPU is running hot and an operation has lots of fields!
         */
        private ImmutableList.@Nullable Builder<Field> fields;
        private @Nullable Field singleField;
        private final ImmutableList.Builder<DeferredExecution> deferredExecutions = new ImmutableList.Builder<>();

        private Builder() {
        }

        private Builder(MergedField existing) {
            if (existing.fields != null) {
                this.singleField = null;
                this.fields = new ImmutableList.Builder<>();
                this.fields.addAll(existing.getFields());
            } else {
                this.singleField = existing.singleField;
            }
            deferredExecutions.addAll(existing.deferredExecutions);
        }

        private ImmutableList.Builder<Field> ensureFieldsListBuilder() {
            if (this.fields == null) {
                this.fields = new ImmutableList.Builder<>();
                if (this.singleField != null) {
                    this.fields.add(this.singleField);
                    this.singleField = null;
                }
            }
            return this.fields;
        }

        public Builder fields(List<Field> fields) {
            this.fields = ensureFieldsListBuilder();
            this.fields.addAll(fields);
            return this;
        }

        public Builder addField(Field field) {
            if (singleField == null && this.fields == null) {
                singleField = field;
                return this;
            } else {
                this.fields = ensureFieldsListBuilder();
            }
            this.fields.add(field);
            return this;
        }

        public Builder addDeferredExecutions(List<DeferredExecution> deferredExecutions) {
            this.deferredExecutions.addAll(deferredExecutions);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addDeferredExecution(@Nullable DeferredExecution deferredExecution) {
            if (deferredExecution != null) {
                this.deferredExecutions.add(deferredExecution);
            }
            return this;
        }

        public MergedField build() {
            ImmutableList<DeferredExecution> deferredExecutions = this.deferredExecutions.build();
            if (this.singleField != null && this.fields == null) {
                return new MergedField(singleField, deferredExecutions);
            }
            ImmutableList<Field> fields = assertNotNull(this.fields, () -> "You MUST add at least one field via the builder").build();
            return new MergedField(fields, deferredExecutions);
        }
    }
}
