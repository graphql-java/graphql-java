package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
class SelectionContainer {

    private final SelectionSet sourceSelectionSet;

    // Tracking the state of computeEffectiveSelections
    private boolean inProgress = false;
    private boolean done = false;

    private List<SelectionContainer> directSpreads = new ArrayList<>();

    private List<SelectionField> directFields = new ArrayList<>();

    /**
     * This selection set and all directly or indirectly included spreads.
     * Indirectly included spreads come from spreads in directly included
     * spreads, etc.
     */
    private final LinkedHashSet<SelectionContainer> effectiveSelections;

    SelectionContainer(SelectionSet sourceSelectionSet) {
        this.sourceSelectionSet = sourceSelectionSet;
        effectiveSelections = new LinkedHashSet<>();
        effectiveSelections.add(SelectionContainer.this);
    }

    public List<SelectionField> getDirectFields() {
        return directFields;
    }

    public Object getSourceSelectionSet() {
        return sourceSelectionSet;
    }

    public void addSpread(SelectionContainer selectionContainer) {
        directSpreads.add(selectionContainer);
    }

    public void addField(SelectionField field) {
        directFields.add(field);
    }

    public SortedArraySet<SelectionField> fieldSet() {
        return SelectionContainer.fieldSet(effectiveSelections);
    }

    public void computeEffectiveSelections() {
        if (inProgress || done) {
            return;
        }
        inProgress = true;
        directFields.forEach(field -> {
            field.getChildSelection().computeEffectiveSelections();
        });
        directSpreads.forEach(spread -> {
            // prevent building cycles
            if (!spread.inProgress) {
                // prevent doing work twice
                if (spread.done) {
                    if (!effectiveSelections.contains(spread)) {
                        effectiveSelections.addAll(spread.effectiveSelections);
                    }
                } else {
                    spread.computeEffectiveSelections();
                    //effective selections of spread are also done after spread.computeEffectiveSelections()
                    effectiveSelections.addAll(spread.effectiveSelections);
                }
            }
        });
        inProgress = false;
        done = true;
    }

    static SortedArraySet<SelectionField> children(SortedArraySet<SelectionField> fields) {
        LinkedHashSet<SelectionContainer> childSelections = new LinkedHashSet<>();
        fields.forEach(f -> childSelections.addAll(f.getChildSelection().effectiveSelections));
        return SelectionContainer.fieldSet(childSelections);
    }

    static SortedArraySet<SelectionField> fieldSet(LinkedHashSet<SelectionContainer> effectiveSelections) {
        int expectedSize = effectiveSelections.stream()
                .mapToInt(selection -> selection.getDirectFields().size())
                .sum();
        SortedArraySet.Builder<SelectionField> builder = SortedArraySet.newBuilder(expectedSize, SelectionField.comparator());
        effectiveSelections.forEach(selection -> builder.addAll(selection.directFields));
        return builder.build();
    }
}
