package graphql.test.agent;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;


public class AgentTest {


    public static void main(String[] args) {
        ByteBuddyAgent.attach(new File("agent/build/libs/agent.jar"), String.valueOf(ProcessHandle.current().pid()));
        TestQuery.executeQuery();
    }

}
