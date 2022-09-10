package graphql.util;

import graphql.Internal;

@Internal
public class BackDoor {


    private static boolean USE_LIGHTWEIGHT_DFS = true;

    @Internal
    public static boolean lightWeightDataFetching() {
        return USE_LIGHTWEIGHT_DFS;
    }

    @Internal
    public static void useLightWeightDataFetching(boolean flag) {
        USE_LIGHTWEIGHT_DFS = flag;
    }

}
