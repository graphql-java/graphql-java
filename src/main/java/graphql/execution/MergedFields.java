package graphql.execution;

import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotEmpty;

/**
 * This represent all Fields in query which overlap and are merged into one.
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
public class MergedFields {

    private final List<Field> fields;

    public MergedFields(List<Field> fields) {
        assertNotEmpty(fields);
        this.fields = new ArrayList<>(fields);
    }

    /**
     * All merged fields have the same name.
     *
     * WARNING: This is not always the key in the execution result, because of possible aliases.
     *
     * @return the name of of the merged fields.
     */
    public String getName() {
        return fields.get(0).getName();
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
     * @return
     */
    public List<Argument> getArguments() {
        return getSingleField().getArguments();
    }


    /**
     * All merged fields
     *
     * @return all merged fields
     */
    public List<Field> getFields() {
        return new ArrayList<>(fields);
    }

    public static Builder newMergedFields() {
        return new Builder();
    }

    public static Builder newMergedFields(Field field) {
        return new Builder().addField(field);
    }

    public static Builder newMergedFields(List<Field> fields) {
        return new Builder().fields(fields);
    }

    public MergedFields transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {
        private List<Field> fields = new ArrayList<>();

        private Builder() {

        }

        private Builder(MergedFields existing) {
            this.fields = existing.getFields();
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public MergedFields build() {
            return new MergedFields(fields);
        }


    }

}
