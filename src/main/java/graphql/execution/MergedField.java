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

import java.util.Collection;
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
 * The actual logic when fields can be successfully merged together is implemented in {#graphql.validation.OperationValidator}
 */
@PublicApi
@NullMarked
public class MergedField {

    private final Field singleField;
    private final ImmutableList<DeferredExecution> deferredExecutions;

    private MergedField(Field field, ImmutableList<DeferredExecution> deferredExecutions) {
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
        return ImmutableList.of(singleField);
    }

    /**
     * @return how many fields are in this merged field
     */
    public int getFieldsCount() {
        return 1;
    }

    /**
     * @return true if the field has a sub selection
     */
    public boolean hasSubSelection() {
        return singleField.getSelectionSet() != null;
    }

    /**
     * @return true if this {@link MergedField} represents a single {@link Field} in the operation
     */
    public boolean isSingleField() {
        return true;
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
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergedField that = (MergedField) o;
        return this.singleField.equals(that.singleField);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(singleField);
    }

    @Override
    public String toString() {
        return "MergedField{" +
                "field(s)=" + singleField +
                '}';
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
        ImmutableList<DeferredExecution> deferredExecutions = mkDeferredExecutions(deferredExecution);
        ImmutableList<Field> fields = ImmutableList.of(singleField, field);
        return new MultiMergedField(fields, deferredExecutions);
    }

    ImmutableList<DeferredExecution> mkDeferredExecutions(@Nullable DeferredExecution deferredExecution) {
        ImmutableList<DeferredExecution> deferredExecutions = this.deferredExecutions;
        if (deferredExecution != null) {
            deferredExecutions = ImmutableKit.addToList(deferredExecutions, deferredExecution);
        }
        return deferredExecutions;
    }

    /**
     * Most of the time we have a single field inside a MergedField but when we need more than one field
     * represented then this {@link MultiMergedField} is used
     */
    static final class MultiMergedField extends MergedField {
        private final ImmutableList<Field> fields;

        MultiMergedField(ImmutableList<Field> fields, ImmutableList<DeferredExecution> deferredExecutions) {
            super(fields.get(0), deferredExecutions);
            this.fields = fields;
        }

        @Override
        public List<Field> getFields() {
            return fields;
        }

        @Override
        public boolean hasSubSelection() {
            for (Field field : this.fields) {
                if (field.getSelectionSet() != null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getFieldsCount() {
            return fields.size();
        }

        @Override
        public boolean isSingleField() {
            return fields.size() == 1;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MultiMergedField that = (MultiMergedField) o;
            return fields.equals(that.fields);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(fields);
        }

        @Override
        public String toString() {
            return "MultiMergedField{" +
                    "field(s)=" + fields +
                    '}';
        }


        @Override
        public void forEach(Consumer<Field> fieldConsumer) {
            fields.forEach(fieldConsumer);
        }

        @Override
        MergedField newMergedFieldWith(Field field, @Nullable DeferredExecution deferredExecution) {
            ImmutableList<DeferredExecution> deferredExecutions = mkDeferredExecutions(deferredExecution);
            ImmutableList<Field> fields = ImmutableKit.addToList(this.fields, field);
            return new MultiMergedField(fields, deferredExecutions);
        }
    }


    public static Builder newMergedField() {
        return new Builder();
    }

    public static Builder newMergedField(Field field) {
        return new Builder().addField(field);
    }

    public static Builder newMergedField(Collection<Field> fields) {
        return new Builder().fields(fields);
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
        fieldConsumer.accept(singleField);
    }

    public static class Builder {

        /*
            The builder logic is complicated by these dual singleton / list duality code,
            but it prevents memory allocation and every bit counts
            when the CPU is running hot and an operation has lots of fields!
         */
        private ImmutableList.@Nullable Builder<Field> fields;
        private @Nullable Field singleField;
        private ImmutableList.@Nullable Builder<DeferredExecution> deferredExecutions;

        private Builder() {
        }

        private Builder(MergedField existing) {
            if (existing instanceof MultiMergedField) {
                this.singleField = null;
                this.fields = new ImmutableList.Builder<>();
                this.fields.addAll(existing.getFields());
            } else {
                this.singleField = existing.singleField;
            }
            if (!existing.deferredExecutions.isEmpty()) {
                this.deferredExecutions = ensureDeferredExecutionsListBuilder();
                this.deferredExecutions.addAll(existing.deferredExecutions);
            }
        }

        private ImmutableList.Builder<DeferredExecution> ensureDeferredExecutionsListBuilder() {
            if (this.deferredExecutions == null) {
                this.deferredExecutions = new ImmutableList.Builder<>();
            }
            return this.deferredExecutions;
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

        public Builder fields(Collection<Field> fields) {
            if (singleField == null && this.fields == null && fields.size() == 1) {
                // even if you present a list - if its a list of one, we dont allocate a list
                singleField = fields.iterator().next();
                return this;
            } else {
                this.fields = ensureFieldsListBuilder();
                this.fields.addAll(fields);
            }
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
            if (!deferredExecutions.isEmpty()) {
                this.deferredExecutions = ensureDeferredExecutionsListBuilder();
                this.deferredExecutions.addAll(deferredExecutions);
            }
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addDeferredExecution(@Nullable DeferredExecution deferredExecution) {
            if (deferredExecution != null) {
                this.deferredExecutions = ensureDeferredExecutionsListBuilder();
                this.deferredExecutions.add(deferredExecution);
            }
            return this;
        }

        public MergedField build() {
            ImmutableList<DeferredExecution> deferredExecutions;
            if (this.deferredExecutions == null) {
                deferredExecutions = ImmutableList.of();
            } else {
                deferredExecutions = this.deferredExecutions.build();
            }
            if (this.singleField != null && this.fields == null) {
                return new MergedField(singleField, deferredExecutions);
            }
            ImmutableList<Field> fields = assertNotNull(this.fields, "You MUST add at least one field via the builder").build();
            assertNotEmpty(fields);
            return new MultiMergedField(fields, deferredExecutions);
        }
    }
}
