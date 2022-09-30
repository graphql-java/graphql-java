package graphql.schema.diffing;

import java.util.List;
import java.util.Set;

public class Util {
    public static void diffNamedList(Set<String> sourceNames,
                                     Set<String> targetNames,
                                     List<String> deleted,
                                     List<String> inserted,
                                     List<String> same) {
        for (String sourceName : sourceNames) {
            if (targetNames.contains(sourceName)) {
                same.add(sourceName);
            } else {
                deleted.add(sourceName);
            }
        }

        for (String targetName : targetNames) {
            if (!sourceNames.contains(targetName)) {
                inserted.add(targetName);
            }
        }
    }

}
