package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.Argument;
import graphql.language.AstPrinter;
import graphql.language.Field;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
final class FieldNameAndArguments {

    private String fieldName;
    private List<String> argumentStringRepresentations;

    public FieldNameAndArguments(Field astField) {
        this.fieldName = astField.getName();
        this.argumentStringRepresentations = astField.getArguments().stream()
                .map(this::toStringRepresentation)
                .sorted()
                .collect(Collectors.toList());
    }

    private String toStringRepresentation(Argument arg) {
        return arg.getName() + ";;" + AstPrinter.printAstCompact(arg.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldNameAndArguments)) {
            return false;
        }
        FieldNameAndArguments that = (FieldNameAndArguments) o;
        return Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(argumentStringRepresentations, that.argumentStringRepresentations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, argumentStringRepresentations);
    }
}
