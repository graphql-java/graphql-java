package graphql.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class StartAgentOnStartupTest {


    @Test
    void testAgentCanBeLoadedAtStartup() throws IOException, InterruptedException {
        // we use the classpath of the current test
        String classPath = System.getProperty("java.class.path");
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-javaagent:../agent/build/libs/agent.jar", "-classpath", classPath, "graphql.GraphQLApp");
        Process process = processBuilder.start();
        process.getErrorStream().transferTo(System.err);
        process.getInputStream().transferTo(System.out);
        int i = process.waitFor();
        assertThat(i).isZero();
    }
}
