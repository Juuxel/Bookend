package juuxel.bookend.server.libraryapi;

import juuxel.bookend.storage.Book;
import juuxel.bookend.storage.BookDb;
import juuxel.bookend.storage.Cover;

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
        try {
            for (var api : libraryApis) {
                int result = insertViaLibrary(api, barcode);
                if (result > 0) return result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return -1;
    }

    private int insertViaLibrary(LibraryApi api, String barcode) throws IOException, InterruptedException {
        var record = api.getFromBarcode(client, barcode);
        if (record == null) return -1;

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

        var book = new Book(-1, record.title(), String.join("; ", record.authors()), record.url(), barcode, coverDbId);
        return db.insert(book);
    }

    @Override
    public void close() {
        client.close();
    }
}
