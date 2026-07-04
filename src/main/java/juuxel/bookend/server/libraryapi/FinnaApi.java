package juuxel.bookend.server.libraryapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FinnaApi implements LibraryApi {
    private static final String API_BASE_URL = "https://api.finna.fi/api/v1";
    private static final List<String> SEARCH_API_URLS = List.of(
        API_BASE_URL + "/search?lookfor=%s&filter%%5B%%5D=%%7Ebuilding%%3A%%220%%2FHelmet%%2F%%22",
        API_BASE_URL + "/search?lookfor=%s"
    );
    private static final String FINNA_BASE_URL = "https://finna.fi";
    private static final String COVER_URL = FINNA_BASE_URL + "/Cover/Show?source=Solr&size=large&recordid=%s&invisbn=%s";
    private static final String NAMESPACE = "finna";

    private final Gson gson = new Gson();

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public @Nullable LibraryRecord getFromBarcode(HttpClient client, String barcode) throws IOException, InterruptedException {
        for (var searchApiUrl : SEARCH_API_URLS) {
            var searchUrl = searchApiUrl.formatted(URLEncoder.encode(barcode, StandardCharsets.UTF_8));
            var searchRequest = HttpRequest.newBuilder(URI.create(searchUrl)).build();
            var searchResponse = client.send(searchRequest, HttpResponse.BodyHandlers.ofString());
            var searchJson = gson.fromJson(searchResponse.body(), JsonObject.class);

            if (searchJson.getAsJsonPrimitive("resultCount").getAsInt() == 0) {
                return null;
            }

            var doc = searchJson.getAsJsonArray("records").get(0).getAsJsonObject();
            var authors = doc.getAsJsonArray("nonPresenterAuthors")
                .asList()
                .stream()
                .map(child -> child.getAsJsonObject().getAsJsonPrimitive("name").getAsString())
                .toList();
            var title = doc.getAsJsonPrimitive("title").getAsString();
            var recordId = doc.getAsJsonPrimitive("id").getAsString();
            var url = "https://finna.fi/Record/" + recordId;
            var images = doc.getAsJsonArray("images");
            var coverUrl = !images.isEmpty() ? FINNA_BASE_URL + images.get(0).getAsJsonPrimitive().getAsString() : COVER_URL.formatted(recordId, barcode);
            return new LibraryRecord(title, authors, url, coverUrl);
        }

        return null;
    }

    @Override
    public @Nullable LibraryCover getCover(HttpClient client, String coverId) throws IOException, InterruptedException {
        var coverRequest = HttpRequest.newBuilder(URI.create(coverId)).build();
        var coverResponse = client.send(coverRequest, HttpResponse.BodyHandlers.ofByteArray());
        var coverMediaType = coverResponse.headers().firstValue("Content-Type").orElseThrow();

        if ("image/gif".equals(coverMediaType)) {
            // Suspicious - discard. This is the content type for missing covers.
            return null;
        }

        var coverData = coverResponse.body();
        return new LibraryCover(coverMediaType, coverData);
    }
}
