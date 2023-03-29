package graphql.parser;

import graphql.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.function.Consumer;

/**
 * This reader will only emit a maximum number of characters from it.  This is used to protect us from evil input.
 * <p>
 * If a graphql system does not have some max HTTP input limit, then this will help protect the system.  This is a limit
 * of last resort.  Ideally the http input should be limited, but if its not, we have this.
 */
@Internal
public class SafeTokenReader extends Reader {

    private final Reader delegate;
    private final int maxCharacters;
    private final Consumer<Integer> whenMaxCharactersExceeded;
    private int count;

    public SafeTokenReader(Reader delegate, int maxCharacters, Consumer<Integer> whenMaxCharactersExceeded) {
        this.delegate = delegate;
        this.maxCharacters = maxCharacters;
        this.whenMaxCharactersExceeded = whenMaxCharactersExceeded;
        count = 0;
    }

    private int checkHowMany(int read, int howMany) {
        if (read != -1) {
            count += howMany;
            if (count > maxCharacters) {
                whenMaxCharactersExceeded.accept(maxCharacters);
            }
        }
        return read;
    }

    @Override
    public int read(char @NotNull [] buff, int off, int len) throws IOException {
        int howMany = delegate.read(buff, off, len);
        return checkHowMany(howMany, howMany);
    }

    @Override
    public int read() throws IOException {
        int ch = delegate.read();
        return checkHowMany(ch, 1);
    }

    @Override
    public int read(@NotNull CharBuffer target) throws IOException {
        int howMany = delegate.read(target);
        return checkHowMany(howMany, howMany);
    }

    @Override
    public int read( char @NotNull [] buff) throws IOException {
        int howMany = delegate.read(buff);
        return checkHowMany(howMany, howMany);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public boolean ready() throws IOException {
        return delegate.ready();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        delegate.mark(readAheadLimit);
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }
}
