package graphql.execution;

public class Path {
    private final Path parent;
    private PathComponent tail;

    public static Path root= new Path();

    private Path() {
        parent = null;
        tail = null;
    }

    public Path(String topLevelField) {
        this(null, new StringPathComponent(topLevelField));
    }

    private Path(Path parent, PathComponent tail) {
        this.parent = parent;
        this.tail = tail;
    }

    public Path withTrailing(String trailing) {
        return new Path(this, new StringPathComponent(trailing));
    }

    public Path withTrailing(int index) {
        return new Path(this, new IntPathComponent(index));
    }

    @Override
    public String toString() {
        if (tail == null) {
            return "";
        }

        if (parent == root) {
            return tail.toString().substring(1);
        }

        return parent.toString() + tail.toString();
    }

    private interface PathComponent {
    }

    private static class StringPathComponent implements PathComponent {
        private final String value;
        public StringPathComponent(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("empty path component");
            }
            this.value = value;
        }
        @Override
        public String toString() {
            return '/' + value;
        }
    }

    private static class IntPathComponent implements PathComponent {
        private final int value;
        public IntPathComponent(int value) {
            this.value = value;
        }
        @Override
        public String toString() {
            return "[" + value + ']';
        }
    }
}
