package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.DirectiveExtensionDefinition;

import static java.lang.String.format;

@Internal
public class DirectiveExtensionMissingBaseError extends BaseError {

    public DirectiveExtensionMissingBaseError(DirectiveExtensionDefinition extension) {
        super(extension,
                format("The extension '@%s' directive %s is missing its base directive definition",
                        extension.getName(), lineCol(extension)));
    }
}
