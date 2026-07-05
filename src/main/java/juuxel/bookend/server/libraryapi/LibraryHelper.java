/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.server.libraryapi;

import juuxel.bookend.storage.Book;
import juuxel.bookend.storage.BookDb;
import juuxel.bookend.storage.Cover;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

public final class LibraryHelper implements Closeable {
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private final BookDb db;
    private final List<LibraryApi> libraryApis = List.of(
        new FinnaApi(),
        new OpenLibraryApi()
    );

    public LibraryHelper(BookDb db) {
        this.db = db;
    }

    public int insertViaLibraries(String barcode) {
        var book = getViaLibraries(barcode);
        if (book == null) return -1;
        return db.insert(book);
    }

    public @Nullable Book getViaLibraries(String barcode) {
        try {
            for (var api : libraryApis) {
                var result = getViaLibrary(api, barcode);
                if (result != null) return result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private @Nullable Book getViaLibrary(LibraryApi api, String barcode) throws IOException, InterruptedException {
        var record = api.getFromBarcode(client, barcode);
        if (record == null) return null;

        int coverDbId = 0;

        if (record.coverId() != null) {
            var existingCover = db.getCoverByLibraryId(api.namespace(), record.coverId());

            if (existingCover != null) {
                coverDbId = existingCover.id();
            } else {
                var cover = api.getCover(client, record.coverId());
                if (cover != null) {
                    coverDbId = db.insert(new Cover(-1, api.namespace(), record.coverId(), cover.mediaType(), cover.data()));
                }
            }
        }

        return new Book(-1, record.title(), String.join("; ", record.authors()), record.url(), barcode, coverDbId, null);
    }

    @Override
    public void close() {
        client.close();
    }
}
