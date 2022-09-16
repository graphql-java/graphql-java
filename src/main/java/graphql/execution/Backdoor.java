package graphql.execution;

import graphql.Internal;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.SimpleInstrumentation;

@Internal
public class Backdoor {

    private static boolean useInstrumentation = true;

    public static boolean isUseInstrumentation() {
        return useInstrumentation;
    }

    public static void setUseInstrumentation(boolean flag) {
        useInstrumentation = flag;
    }

    public static Instrumentation instrumentationToUse(Instrumentation instrumentation) {
        if (isUseInstrumentation()) {
            return instrumentation;
        }
        return SimpleInstrumentation.INSTANCE; // a no op
    }
}
