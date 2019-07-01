package graphql.execution.nextgen.depgraph;

import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.List;

public class FieldWTC {

    private final Field field;
    private final List<String> typeConditions;

    public FieldWTC(Field field, List<String> typeConditions) {
        this.field = field;
        this.typeConditions = typeConditions;
    }

    public Field getField() {
        return field;
    }

    public List<String> getTypeConditions() {
        return typeConditions;
    }

    public String getName() {
        return field.getName();
    }

    public String getAlias() {
        return field.getAlias();
    }

    public List<Argument> getArguments() {
        return field.getArguments();
    }

    public SelectionSet getSelectionSet() {
        return field.getSelectionSet();
    }

    @Override
    public String toString() {
        return "FieldWTC{" +
                "field=" + field.getName() +
                ", typeConditions=" + typeConditions +
                '}';
    }
}
