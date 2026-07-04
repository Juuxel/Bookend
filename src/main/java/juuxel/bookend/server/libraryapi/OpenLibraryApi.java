package juuxel.bookend.server.libraryapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import juuxel.bookend.storage.Book;
import juuxel.bookend.storage.BookDb;
import juuxel.bookend.storage.Cover;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class OpenLibraryApi implements LibraryApi {
    private static final String SEARCH_API_URL = "https://openlibrary.org/search.json?q=%s";
    private static final String COVERS_API_URL = "https://covers.openlibrary.org/b/id/%s-M.jpg";
    private static final String NAMESPACE = "openlibrary";

    private final Gson gson = new Gson();

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public @Nullable LibraryRecord getFromBarcode(HttpClient client, String barcode) throws IOException, InterruptedException {
        var searchUrl = SEARCH_API_URL.formatted(URLEncoder.encode(barcode, StandardCharsets.UTF_8));
        var searchRequest = HttpRequest.newBuilder(URI.create(searchUrl)).build();
        var searchResponse = client.send(searchRequest, HttpResponse.BodyHandlers.ofString());
        var searchJson = gson.fromJson(searchResponse.body(), JsonObject.class);

        if (searchJson.getAsJsonPrimitive("numFound").getAsInt() == 0) {
            return null;
        }

        var doc = searchJson.getAsJsonArray("docs").get(0).getAsJsonObject();
        List<String> authors = doc.has("author_name") ? doc.getAsJsonArray("author_name").asList().stream().map(child -> child.getAsJsonPrimitive().getAsString()).toList() : List.of();
        var title = doc.getAsJsonPrimitive("title").getAsString();
        var url = "https://openlibrary.org" + doc.getAsJsonPrimitive("key").getAsString();
        var coverI = doc.has("cover_i") ? "" + doc.getAsJsonPrimitive("cover_i").getAsInt() : null;
        return new LibraryRecord(title, authors, url, coverI);
    }

    @Override
    public LibraryCover getCover(HttpClient client, String coverId) throws IOException, InterruptedException {
        var coverUrl = COVERS_API_URL.formatted(coverId);
        var coverRequest = HttpRequest.newBuilder(URI.create(coverUrl))
            .build();
        var coverResponse = client.send(coverRequest, HttpResponse.BodyHandlers.ofByteArray());
        var coverMediaType = "image/jpeg";
        var coverData = coverResponse.body();
        return new LibraryCover(coverMediaType, coverData);
    }
}
