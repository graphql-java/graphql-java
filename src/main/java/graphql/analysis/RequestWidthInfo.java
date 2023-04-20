package graphql.analysis;

import graphql.PublicApi;

/**
 * The request width info.
 */
@PublicApi
public class RequestWidthInfo {
    private final int width;

    private RequestWidthInfo(int width) {
        this.width = width;
    }

    /**
     * This returns the request width.
     *
     * @return the request width
     */
    public int getWidth() {
        return width;
    }

    @Override
    public String toString() {
        return "RequestWidthInfo{" +
                "width=" + width +
                '}';
    }

    /**
     * @return a new {@link RequestWidthInfo} builder
     */
    public static RequestWidthInfo.Builder newRequestWidthInfo() {
        return new RequestWidthInfo.Builder();
    }

    @PublicApi
    public static class Builder {

        private int width;

        private Builder() {
        }

        /**
         * The request width.
         *
         * @param width the request width
         * @return this builder
         */
        public RequestWidthInfo.Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * @return a built {@link RequestWidthInfo} object
         */
        public RequestWidthInfo build() {
            return new RequestWidthInfo(width);
        }
    }
}
