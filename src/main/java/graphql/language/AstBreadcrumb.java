package graphql.language;

import graphql.PublicApi;

@PublicApi
public class AstBreadcrumb {
    private final Node node;
    private final Location location;

    public AstBreadcrumb(Node node, Location location) {
        this.node = node;
        this.location = location;
    }

    public Node getNode() {
        return node;
    }

    public Location getLocation() {
        return location;
    }

    public static class Location {
        private final String name;
        private final int index;

        public Location(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }
    }

}

