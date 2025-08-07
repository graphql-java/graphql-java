package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullUnmarked;

import java.util.List;
import java.util.Map;

@PublicApi
@NullUnmarked
public interface NodeBuilder {

    NodeBuilder sourceLocation(SourceLocation sourceLocation);

    NodeBuilder comments(List<Comment> comments);

    NodeBuilder ignoredChars(IgnoredChars ignoredChars);

    NodeBuilder additionalData(Map<String, String> additionalData);

    NodeBuilder additionalData(String key, String value);

}
