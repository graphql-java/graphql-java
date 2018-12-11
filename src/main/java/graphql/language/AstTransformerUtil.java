package graphql.language;

import graphql.PublicApi;
import graphql.util.TraverserContext;

@PublicApi
public class AstTransformerUtil {

    /**
     * Helper method to be used inside a {@link NodeVisitor} to actually a change a node.<p/>
     * It generates a new {@link AstZipper} and replaces the current accumulated {@link AstMultiZipper} including the new
     * {@link AstZipper}.
     *
     * @param context
     * @param changedNode
     */
    public static void changeNode(TraverserContext<Node> context, Node changedNode) {
        AstZipper zipperWithChangedNode = context.getVar(AstZipper.class).withNewNode(changedNode);
        AstMultiZipper multiZipper = context.getCurrentAccumulate();
        context.setAccumulate(multiZipper.withNewZipper(zipperWithChangedNode));
    }


}
