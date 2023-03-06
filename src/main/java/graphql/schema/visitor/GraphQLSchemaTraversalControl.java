package graphql.schema.visitor;

import graphql.PublicApi;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;

/**
 * This indicates what traversal control to apply during the visitation
 * and can be created via calls to methods like {@link GraphQLSchemaVisitorEnvironment#ok()}
 * or {@link GraphQLSchemaVisitorEnvironment#changeNode(GraphQLSchemaElement)} say
 */
@PublicApi
public class GraphQLSchemaTraversalControl {
    private final GraphQLSchemaElement element;
    private final Control control;

    enum Control {
        CONTINUE(TraversalControl.CONTINUE),
        QUIT(TraversalControl.QUIT),
        CHANGE(TraversalControl.CONTINUE),
        DELETE(TraversalControl.CONTINUE),
        INSERT_BEFORE(TraversalControl.CONTINUE),
        INSERT_AFTER(TraversalControl.CONTINUE);

        private final TraversalControl traversalControl;

        Control(TraversalControl traversalControl) {
            this.traversalControl = traversalControl;
        }

        public TraversalControl toTraversalControl() {
            return traversalControl;
        }
    }

    static final GraphQLSchemaTraversalControl CONTINUE = new GraphQLSchemaTraversalControl(Control.CONTINUE, null);
    static final GraphQLSchemaTraversalControl QUIT = new GraphQLSchemaTraversalControl(Control.QUIT, null);
    static final GraphQLSchemaTraversalControl DELETE = new GraphQLSchemaTraversalControl(Control.DELETE, null);

    GraphQLSchemaTraversalControl(Control control, GraphQLSchemaElement element) {
        this.element = element;
        this.control = control;
    }

    GraphQLSchemaElement getElement() {
        return element;
    }

    Control getControl() {
        return control;
    }

    boolean isAbortive() {
        return control == Control.QUIT;
    }

    boolean isMutative() {
        return control == Control.DELETE || control == Control.CHANGE || control == Control.INSERT_AFTER || control == Control.INSERT_BEFORE;
    }

    TraversalControl toTraversalControl(TraverserContext<GraphQLSchemaElement> context) {
        if (control == Control.CONTINUE || control == Control.QUIT) {
            return control.toTraversalControl();
        }
        if (control == Control.DELETE) {
            TreeTransformerUtil.deleteNode(context);
        }
        if (control == Control.CHANGE) {
            TreeTransformerUtil.changeNode(context, element);
        }
        if (control == Control.INSERT_AFTER) {
            TreeTransformerUtil.insertAfter(context, element);
        }
        if (control == Control.INSERT_BEFORE) {
            TreeTransformerUtil.insertAfter(context, element);
        }
        return TraversalControl.CONTINUE;
    }
}
