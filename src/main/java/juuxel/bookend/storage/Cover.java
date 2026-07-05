/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.storage;

import org.jspecify.annotations.Nullable;

public record Cover(int id, @Nullable String libraryNs, @Nullable String libraryCoverId, String mediaType, byte[] blob) {
}
