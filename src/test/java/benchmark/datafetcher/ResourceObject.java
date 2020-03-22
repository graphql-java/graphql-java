package benchmark.datafetcher;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ResourceObject {

    private String privateProperty = "privateValue";

    private String publicProperty = "publicValue";

    public String publicField = "publicFieldValue";

    private String privateField = "privateFieldValue";

    private String getPrivateProperty() {
        return privateProperty;
    }

    public String getPublicProperty() {
        return publicProperty;
    }
}