package graphql.schema.universe;

import graphql.ExperimentalApi;

/**
 * Options controlling schema-universe imports.
 */
@ExperimentalApi
public final class SUSchemaOptions {

    private static final SUSchemaOptions DEFAULT_OPTIONS =
            new SUSchemaOptions(true);

    private final boolean captureAstDefinitions;

    private SUSchemaOptions(boolean captureAstDefinitions) {
        this.captureAstDefinitions = captureAstDefinitions;
    }

    public static SUSchemaOptions defaultOptions() {
        return DEFAULT_OPTIONS;
    }

    public boolean isCaptureAstDefinitions() {
        return captureAstDefinitions;
    }

    /**
     * Controls whether source AST definitions and extensions are retained.
     *
     * <p>Disabling capture saves memory, but AST-preserving schema printing and source provenance
     * are unavailable.</p>
     *
     * @param captureAstDefinitions whether AST provenance is retained
     *
     * @return updated immutable options
     */
    public SUSchemaOptions captureAstDefinitions(
            boolean captureAstDefinitions) {
        if (this.captureAstDefinitions == captureAstDefinitions) {
            return this;
        }
        return new SUSchemaOptions(captureAstDefinitions);
    }
}
