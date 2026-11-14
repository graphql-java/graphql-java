package graphql.validation;

import graphql.Internal;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.normalized.NormalizedDocument;
import graphql.normalized.NormalizedField;
import graphql.normalized.NormalizedOperation;
import graphql.normalized.NormalizedSelection;
import graphql.normalized.NormalizedSelectionScope;
import graphql.normalized.NormalizedSelectionSet;
import graphql.schema.GraphQLCompositeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

@Internal
public final class NormalizedDocumentBuilder {
    private final ValidationContext context;
    private final Map<SelectionSet, Integer> selectionSetIds = new LinkedHashMap<>();
    private final List<SelectionSet> selectionSets = new ArrayList<>();
    private final Map<Field, NormalizedField> fields = new LinkedHashMap<>();

    public NormalizedDocumentBuilder(ValidationContext context) {
        this.context = context;
    }

    public int selectionSet(SelectionSet selectionSet) {
        Integer existing = selectionSetIds.get(selectionSet);
        if (existing != null) {
            return existing;
        }
        int id = selectionSets.size();
        selectionSetIds.put(selectionSet, id);
        selectionSets.add(selectionSet);
        return id;
    }

    public void field(Field field, GraphQLCompositeType parentType) {
        if (fields.containsKey(field)) {
            return;
        }
        SelectionSet children = field.getSelectionSet();
        fields.put(field, NormalizedField.newNormalizedField()
                .source(field)
                .parentTypeName(parentType.getName())
                .selectionSetId(children == null ? -1 : selectionSet(children))
                .build());
    }

    public NormalizedDocument build() {
        Document document = context.getDocument();
        List<NormalizedOperation> operations = new ArrayList<>();
        for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
            operations.add(NormalizedOperation.newNormalizedOperation().source(operation)
                    .selectionSetId(selectionSet(operation.getSelectionSet())).build());
        }
        List<NormalizedSelectionSet> result = new ArrayList<>();
        for (int index = 0; index < selectionSets.size(); index++) {
            result.add(buildSelectionSet(selectionSets.get(index)));
        }
        return NormalizedDocument.newNormalizedDocument().document(document)
                .operations(operations).selectionSets(result).fields(fields).build();
    }

    private NormalizedSelectionSet buildSelectionSet(SelectionSet selectionSet) {
        List<NormalizedSelection> selections = new ArrayList<>();
        for (Selection<?> selection : selectionSet.getSelections()) {
            selections.add(buildSelection(selection));
        }
        return NormalizedSelectionSet.newNormalizedSelectionSet().selections(selections).build();
    }

    private NormalizedSelection buildSelection(Selection<?> selection) {
        if (selection instanceof Field field) {
            return assertNotNull(fields.get(field));
        }
        if (selection instanceof InlineFragment inline) {
            return NormalizedSelectionScope.newNormalizedSelectionScope().source(inline)
                    .typeCondition(inline.getTypeCondition() == null ? null : inline.getTypeCondition().getName())
                    .selectionSetId(selectionSet(inline.getSelectionSet())).build();
        }
        FragmentSpread spread = (FragmentSpread) selection;
        FragmentDefinition fragment = assertNotNull(context.getFragment(spread.getName()));
        return NormalizedSelectionScope.newNormalizedSelectionScope().source(spread)
                .typeCondition(fragment.getTypeCondition().getName()).fragmentDefinition(fragment)
                .selectionSetId(selectionSet(fragment.getSelectionSet())).build();
    }
}
