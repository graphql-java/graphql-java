package graphql.relay;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@PublicApi
@NullMarked
public class DefaultPageInfo implements PageInfo {

    private final @Nullable ConnectionCursor startCursor;
    private final @Nullable ConnectionCursor endCursor;
    private final boolean hasPreviousPage;
    private final boolean hasNextPage;

    public DefaultPageInfo(@Nullable ConnectionCursor startCursor, @Nullable ConnectionCursor endCursor, boolean hasPreviousPage, boolean hasNextPage) {
        this.startCursor = startCursor;
        this.endCursor = endCursor;
        this.hasPreviousPage = hasPreviousPage;
        this.hasNextPage = hasNextPage;
    }

    @Override
    public @Nullable ConnectionCursor getStartCursor() {
        return startCursor;
    }

    @Override
    public @Nullable ConnectionCursor getEndCursor() {
        return endCursor;
    }

    @Override
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    @Override
    public boolean isHasNextPage() {
        return hasNextPage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultPageInfo that = (DefaultPageInfo) o;
        return Objects.equals(startCursor, that.startCursor) &&
                Objects.equals(endCursor, that.endCursor) &&
                Objects.equals(hasPreviousPage, that.hasPreviousPage) &&
                Objects.equals(hasNextPage, that.hasNextPage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startCursor, endCursor, hasPreviousPage, hasNextPage);
    }

    @Override
    public String toString() {
        return "DefaultPageInfo{" +
                " startCursor=" + startCursor +
                ", endCursor=" + endCursor +
                ", hasPreviousPage=" + hasPreviousPage +
                ", hasNextPage=" + hasNextPage +
                '}';
    }
}
