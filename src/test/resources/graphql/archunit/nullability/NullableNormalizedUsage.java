package nullability;

import graphql.analysis.QueryReducer;
import graphql.analysis.QueryTraverser;
import graphql.language.FragmentDefinition;
import graphql.normalized.NormalizedDocument;
import graphql.normalized.NormalizedSelectionScope;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@NullMarked
public class NullableNormalizedUsage {
    public static @Nullable String reduce(QueryTraverser traverser) {
        QueryReducer<@Nullable String> reducer = (environment, previous) -> null;
        traverser.reducePreOrder(reducer, null);
        return traverser.reducePostOrder(reducer, null);
    }

    public static void absentMetadata(NormalizedSelectionScope scope,
                                      PreparsedDocumentEntry entry) {
        @Nullable String condition = scope.getTypeCondition();
        @Nullable FragmentDefinition fragment = scope.getFragmentDefinition();
        @Nullable NormalizedDocument document = entry.getNormalizedDocument();
    }
}
