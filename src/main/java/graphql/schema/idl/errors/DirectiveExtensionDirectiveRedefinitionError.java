package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;

import static java.lang.String.format;

@Internal
public class DirectiveExtensionDirectiveRedefinitionError extends BaseError {

    public DirectiveExtensionDirectiveRedefinitionError(DirectiveDefinition definition, Directive directive) {
        super(definition,
                format("The directive '@%s' %s has redefined the non-repeatable directive '@%s'",
                        definition.getName(), lineCol(definition), directive.getName()));
    }
}
