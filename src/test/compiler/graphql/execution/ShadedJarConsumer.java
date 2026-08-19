package graphql.execution;

/**
 * Compilation fixture used only by the {@code compileShadedJarConsumer} Gradle task.
 *
 * <p>Calling {@link ExecutionContext#getOperationDirectives()} forces javac to inspect the
 * shaded {@code ImmutableList} in its generic return type. The task compiles this fixture
 * against the completed JAR with {@code -Xlint:classfile -Werror}, turning unresolved
 * annotation references in shaded class files into a build failure.</p>
 */
public class ShadedJarConsumer {

    public Object getOperationDirectives(ExecutionContext executionContext) {
        var operationDirectives = executionContext.getOperationDirectives();
        return operationDirectives;
    }
}
