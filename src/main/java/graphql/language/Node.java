package graphql.language;


import java.util.List;

public interface Node {

     List<Node> getChildren();

     SourceLocation getSourceLocation();
}
