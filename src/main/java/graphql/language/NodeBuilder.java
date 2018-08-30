package graphql.language;

import graphql.PublicApi;

import java.util.List;

@PublicApi
public interface NodeBuilder {

    NodeBuilder sourceLocation(SourceLocation sourceLocation);

    NodeBuilder comments(List<Comment> comments);
}
