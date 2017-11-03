package graphql.validation;


import graphql.Internal;
import graphql.language.Node;
import graphql.schema.visibility.GraphqlFieldVisibilityEnvironment;

import java.util.List;

@Internal
public interface DocumentVisitor {

    void enter(Node node, List<Node> path, GraphqlFieldVisibilityEnvironment fieldVisibilityEnvironment);

    void leave(Node node, List<Node> path, GraphqlFieldVisibilityEnvironment fieldVisibilityEnvironment);
}
