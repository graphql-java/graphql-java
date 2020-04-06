package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLOutputType;

import java.util.Comparator;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
class SelectionField {

    int id;
    Field astField;
    GraphQLCompositeType parentType;
    GraphQLOutputType outputType;

    final SelectionContainer selectionContainer;

    public SelectionField(int id, Field astField, GraphQLCompositeType parentType, GraphQLOutputType outputType) {
        this.id = id;
        this.astField = astField;
        this.parentType = parentType;
        this.outputType = outputType;
        this.selectionContainer = new SelectionContainer(astField.getSelectionSet());
    }

    String getOutputName() {
        return astField.getAlias() == null ? astField.getName() : astField.getAlias();
    }

    TypeAbstractness parentTypeAbstractness() {
        return TypeAbstractness.apply(parentType);
    }

    TypeShape outputTypeShape() {
        return TypeShape.apply(outputType);
    }

    FieldNameAndArguments fieldNameAndArguments() {
        return new FieldNameAndArguments(astField);
    }

    public SelectionContainer getChildSelection() {
        return selectionContainer;
    }

    @Override
    public String toString() {
        return "SelectionField{" +
                "id=" + id +
                ",name=" + astField.getName() +
                ",out=" + getOutputName() +
                '}';
    }

    static class Builder {

        private int id = 0;

        SelectionField build(
                Field astField,
                GraphQLCompositeType parentType,
                GraphQLOutputType outputType
        ) {
            id += 1;
            return new SelectionField(
                    id,
                    astField,
                    parentType,
                    outputType
            );
        }
    }

    /**
     * This gives us a stable order of fields in sets.
     * It is determined by the order of traversal of the query.
     */
    public static Comparator<SelectionField> comparator() {
        return Comparator.comparingInt(x -> x.id);
    }

    static SortedArraySet<SelectionField> children(SortedArraySet<SelectionField> fields) {
        return SelectionContainer.children(fields);
    }
}
