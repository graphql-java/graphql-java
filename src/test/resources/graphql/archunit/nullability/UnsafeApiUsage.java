package nullability;

import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitor;
import graphql.schema.DelegatingDataFetchingEnvironment;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class UnsafeApiUsage {
    public static String argument(DelegatingDataFetchingEnvironment environment) {
        return environment.<String>getArgumentOrDefault("arg", "fallback").toString();
    }

    public static String traversal(Node<?> node, NodeVisitor visitor) {
        return new NodeTraverser().depthFirst(visitor, node).toString();
    }

    public static String shared(TraverserContext<?> context) {
        return context.<String>getSharedContextData().toString();
    }

    public static String current(TraverserContext<?> context) {
        return context.<String>getCurrentAccumulate().toString();
    }

    public static String next(TraverserContext<?> context) {
        return context.<String>getNewAccumulate().toString();
    }
}
