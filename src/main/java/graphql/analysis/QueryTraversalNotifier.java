package graphql.analysis;

import graphql.Assert;
import graphql.Internal;
import java.util.function.Consumer;

/**
 * QueryTraversal helper class responsible for notifications to the 
 * provided preOrder and postOrder consumers
 */
@Internal
class QueryTraversalNotifier {
    
    private final Consumer<QueryVisitorEnvironment> preOrder;
    private final Consumer<QueryVisitorEnvironment> postOrder;    
    
    QueryTraversalNotifier(Consumer<QueryVisitorEnvironment> preOrder, Consumer<QueryVisitorEnvironment> postOrder) {
        this.preOrder = Assert.assertNotNull(preOrder);
        this.postOrder = Assert.assertNotNull(postOrder);
    }

    void notifyPreOrder(QueryVisitorEnvironment env) {
        preOrder.accept(env);
    }

    void notifyPostOrder(QueryVisitorEnvironment env) {
        postOrder.accept(env);
    }
}
