package graphql.parser;

import graphql.PublicApi;
import graphql.i18n.I18n;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;

import static graphql.Assert.assertNotNull;

/**
 * This is the arguments that can be passed to a {@link Parser}
 */
@PublicApi
@NullMarked
public interface ParserEnvironment {

    /**
     * @return the document to be parsed
     */
    Reader getDocument();

    /**
     * @return the parsing options
     */
    @Nullable ParserOptions getParserOptions();

    /**
     * @return the locale to produce parsing error messages in
     */
    Locale getLocale();

    /**
     * @return the {@link I18n} to produce parsing error messages with
     */
    I18n getI18N();

    /**
     * @return a builder of new parsing options
     */
    static Builder newParserEnvironment() {
        return new Builder();
    }

    @NullUnmarked
    class Builder {
        Reader reader;
        ParserOptions parserOptions = ParserOptions.getDefaultParserOptions();
        Locale locale = Locale.getDefault();

        public Builder() {
        }

        public Builder document(Reader documentText) {
            this.reader = assertNotNull(documentText);
            return this;
        }

        public Builder document(String documentText) {
            return document(new StringReader(documentText));
        }

        public Builder parserOptions(ParserOptions parserOptions) {
            this.parserOptions = parserOptions;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = assertNotNull(locale);
            return this;
        }

        public ParserEnvironment build() {
            I18n i18n = I18n.i18n(I18n.BundleType.Parsing, locale);
            return new ParserEnvironment() {
                @Override
                public Reader getDocument() {
                    return reader;
                }

                @Override
                public ParserOptions getParserOptions() {
                    return parserOptions;
                }

                @Override
                public Locale getLocale() {
                    return locale;
                }

                @Override
                public I18n getI18N() {
                    return i18n;
                }
            };
        }
    }
}
