package graphql.test;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;


public class LoadAgent {


    public static void load() {
        ByteBuddyAgent.attach(new File("../agent/build/libs/agent.jar"), String.valueOf(ProcessHandle.current().pid()));
    }

}
