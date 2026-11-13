package nullability;

import graphql.analysis.QueryReducer;
import graphql.analysis.QueryTraverser;
import graphql.normalized.nf.NormalizedField;
import graphql.normalized.nf.NormalizedOperation;
import graphql.normalized.nf.NormalizedOperationToAstCompiler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@NullMarked
public class NullableNormalizedUsage {
    public static @Nullable String reduce(QueryTraverser traverser) {
        QueryReducer<@Nullable String> reducer = (environment, previous) -> null;
        traverser.reducePreOrder(reducer, null);
        return traverser.reducePostOrder(reducer, null);
    }

    public static void absentMetadata(NormalizedField field, NormalizedOperation operation,
                                      NormalizedOperationToAstCompiler.CompilerResult compiled) {
        @Nullable String name = operation.getOperationName();
        @Nullable String alias = field.getAlias();
        @Nullable NormalizedField parent = field.getParent();
        @Nullable Map<String, @Nullable Object> variables = compiled.getVariables();
        field.replaceParent(null);
    }
}
