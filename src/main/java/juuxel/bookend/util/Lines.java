/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.util;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class Lines {
    public static List<String> nonEmptyLines(@Nullable String s) {
        if (s == null || s.isEmpty()) return List.of();
        return Arrays.stream(s.split("\n")).filter(line -> !line.isEmpty()).toList();
    }
}
