package juuxel.bookend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Logging {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static Logger logger() {
        return LoggerFactory.getLogger(STACK_WALKER.getCallerClass());
    }
}
