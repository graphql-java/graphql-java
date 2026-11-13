package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@PublicApi
@NullMarked
public interface NodeDirectivesBuilder extends NodeBuilder {

    NodeDirectivesBuilder directives(List<Directive> directives);

    NodeDirectivesBuilder directive(Directive directive);
}
