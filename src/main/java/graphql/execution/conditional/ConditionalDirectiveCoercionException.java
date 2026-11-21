package graphql.execution.conditional;

import graphql.GraphqlErrorException;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Internal
@NullMarked
public class ConditionalDirectiveCoercionException extends GraphqlErrorException {

    public ConditionalDirectiveCoercionException(Directive directive, RuntimeException cause) {
        super(GraphqlErrorException.newErrorException()
                .message("Invalid argument for directive '@" + directive.getName() + "': " + cause.getMessage())
                .sourceLocation(argumentLocation(directive))
                .cause(cause));
    }

    private static @Nullable SourceLocation argumentLocation(Directive directive) {
        Argument argument = directive.getArgument("if");
        return argument != null ? argument.getValue().getSourceLocation() : directive.getSourceLocation();
    }
}
