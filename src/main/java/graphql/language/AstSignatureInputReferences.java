package graphql.language;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Internal
@NullMarked
public class AstSignatureInputReferences {

    private final Set<String> fieldCoordinates = new TreeSet<>();
    private final Set<String> usedDirectives = new TreeSet<>();
    private final Set<String> fieldArgumentCoordinates = new TreeSet<>();
    private final Set<String> directiveArgumentCoordinates = new TreeSet<>();
    private final Set<String> inputObjectFieldCoordinates = new TreeSet<>();

    public void addFieldCoordinate(String fieldCoordinate) {
        fieldCoordinates.add(fieldCoordinate);
    }

    public void addUsedDirective(String usedDirective) {
        usedDirectives.add(usedDirective);
    }

    public void addFieldArgumentCoordinate(String fieldArgumentCoordinate) {
        fieldArgumentCoordinates.add(fieldArgumentCoordinate);
    }

    public void addDirectiveArgumentCoordinate(String directiveArgumentCoordinate) {
        directiveArgumentCoordinates.add(directiveArgumentCoordinate);
    }

    public void addInputObjectFieldCoordinate(String inputObjectFieldCoordinate) {
        inputObjectFieldCoordinates.add(inputObjectFieldCoordinate);
    }

    public void addAll(AstSignatureInputReferences references) {
        fieldCoordinates.addAll(references.fieldCoordinates);
        usedDirectives.addAll(references.usedDirectives);
        fieldArgumentCoordinates.addAll(references.fieldArgumentCoordinates);
        directiveArgumentCoordinates.addAll(references.directiveArgumentCoordinates);
        inputObjectFieldCoordinates.addAll(references.inputObjectFieldCoordinates);
    }

    public List<String> getFieldCoordinates() {
        return new ArrayList<>(fieldCoordinates);
    }

    public List<String> getUsedDirectives() {
        return new ArrayList<>(usedDirectives);
    }

    public List<String> getFieldArgumentCoordinates() {
        return new ArrayList<>(fieldArgumentCoordinates);
    }

    public List<String> getDirectiveArgumentCoordinates() {
        return new ArrayList<>(directiveArgumentCoordinates);
    }

    public List<String> getInputObjectFieldCoordinates() {
        return new ArrayList<>(inputObjectFieldCoordinates);
    }
}
