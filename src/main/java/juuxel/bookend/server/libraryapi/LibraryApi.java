/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.server.libraryapi;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public interface LibraryApi {
    String namespace();
    @Nullable LibraryRecord getFromBarcode(HttpClient client, String barcode) throws IOException, InterruptedException;
    @Nullable LibraryCover getCover(HttpClient client, String coverId) throws IOException, InterruptedException;

    record LibraryRecord(String title, List<String> authors, @Nullable String url, @Nullable String coverId) {
    }

    record LibraryCover(String mediaType, byte[] data) {
    }
}
