/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Logging {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static Logger logger() {
        return LoggerFactory.getLogger(STACK_WALKER.getCallerClass());
    }
}
