/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.server;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.HttpStatus;
import juuxel.bookend.config.Config;
import juuxel.bookend.server.libraryapi.LibraryHelper;
import juuxel.bookend.storage.Book;
import juuxel.bookend.storage.BookDb;
import juuxel.bookend.template.TemplateManager;
import juuxel.bookend.util.Logging;
import juuxel.bookend.util.QrGenerator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class Main {
    private static final Logger LOGGER = Logging.logger();

    private BookDb db;

    void main() {
        var config = new Config();
        LOGGER.info("Launching Bookend on port {}", config.port);
        var templateManager = new TemplateManager();
        db = BookDb.open(Path.of("bookend.db"));
        var app = Javalin.create(c -> {
                c.staticFiles.add(sfc -> {
                    sfc.directory = "/static";
                    sfc.hostedPath = "/static";
                });
                c.events.serverStopping(() -> db.close());
            })
            .get("/", ctx -> ctx.html(templateManager.loadTemplate("FrontPage")))
            .get("/add", ctx -> ctx.html(templateManager.loadTemplate("AddBooks")))
            .get("/add-via-libraries", ctx -> ctx.html(templateManager.loadTemplate("AddBooksViaLibraries")))
            .get("/super", ctx -> {
                Map<String, @Nullable Object> templateCtx = new HashMap<>();
                List<Book> books = new ArrayList<>();

                for (String book : ctx.queryParams("book")) {
                    books.addAll(db.getBooksByCode(book));
                }

                templateCtx.put("books", books);
                templateCtx.put("existingBarcode", ctx.queryParam("existingBarcode"));
                templateCtx.put("fillInDetailsManually", Boolean.parseBoolean(Objects.requireNonNullElse(ctx.queryParam("fillInDetailsManually"), "false")));

                ctx.html(templateManager.loadTemplate("DynamicAddOrView", templateCtx));
            })
            .get("/book/{code}", ctx -> {
                List<Book> books = db.getBooksByCode(ctx.pathParam("code"));

                switch (books.size()) {
                    case 1:
                        var tags = db.getTags(books.getFirst().id());
                        var url = "/book/" + URLEncoder.encode(ctx.pathParam("code"), StandardCharsets.UTF_8);
                        ctx.html(templateManager.loadTemplate("ViewBook", Map.of("book", books.getFirst(), "url", url, "tags", tags)));
                        break;
                    case 0:
                        ctx.status(HttpStatus.NOT_FOUND);
                    default:
                        ctx.html(templateManager.loadTemplate("ViewBookDisambiguation", Map.of("books", books)));
                }
            })
            .get("/tag/{tag}", ctx -> {
                List<Book> books = db.getBooksByTag(ctx.pathParam("tag"));
                ctx.html(templateManager.loadTemplate("ViewBookDisambiguation", Map.of("books", books)));
            })
            .get("/search", ctx -> {
                ctx.redirect("/book/" + URLEncoder.encode(ctx.queryParam("q"), StandardCharsets.UTF_8), HttpStatus.SEE_OTHER);
            })
            .get("/qr", ctx -> ctx.html(templateManager.loadTemplate("GenerateQr")))
            .get("/api/all", ctx -> {
                var joiner = new StringJoiner(
                    "</tr><tr>",
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                    <meta charset="UTF-8">
                    <style>
                    tr:nth-child(even) {
                        background-color: #EEE;
                    }
                    td:nth-child(2) {
                        text-align: center;
                    }
                    </style>
                    </head>
                    <body>
                    <table>
                    <tr>
                    <th>ID</th>
                    <th>Cover</th>
                    <th>Title</th>
                    <th>Author</th>
                    <th>URL</th>
                    <th>Barcode</th>
                    </tr>
                    <tr>
                    """,
                    """
                    </tr>
                    </body>
                    </html>
                   """
                );

                for (Book book : db.getAllBooks()) {
                    var coverHtml = book.cover() != 0 ? "<a href=\"/book/%d\"><img height=100 src=\"/api/cover/%s\" alt=\"cover\"></a>".formatted(book.id(), book.cover()) : "";
                    joiner.add(
                        """
                        <td><a href="/book/%d">%d</a></td>
                        <td>%s</td>
                        <td><a href="/book/%d">%s</a></td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        """.formatted(book.id(), book.id(), coverHtml, book.id(), book.title(), book.author(), book.url(), book.barcode())
                    );
                }

                ctx.html(joiner.toString());
            })
            .get("/api/cover/{code}", ctx -> {
                var cover = db.getCoverById(Integer.parseInt(ctx.pathParam("code")));
                if (cover == null) {
                    ctx.status(HttpStatus.NOT_FOUND);
                    return;
                }
                ctx.contentType(cover.mediaType());
                ctx.result(cover.blob());
            })
            .post("/api/mass-insert", ctx -> {
                var barcodes = ctx.queryParam("barcodes");

                if (barcodes == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Mass insertion missing 'barcodes' query param");
                    return;
                }

                db.massInsert(List.of(barcodes.split(",")));
                ctx.status(HttpStatus.ACCEPTED).result("OK");
            })
            .post("/api/insert", ctx -> {
                var title = ctx.formParam("title");

                if (title == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Insertion missing 'title' query param");
                    return;
                }

                var author = ctx.formParam("author");
                var url = ctx.formParam("url");
                var barcode = ctx.formParam("barcode");
                var note = ctx.formParam("note");

                int bookId = db.insert(new Book(-1, title, author, url, barcode, 0, note));
                ctx.redirect("/book/" + Objects.requireNonNullElse(barcode, "" + bookId), HttpStatus.SEE_OTHER);
            })
            .post("/api/insert-with-library-apis", ctx -> {
                var barcode = ctx.formParam("barcode");

                if (barcode == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Insertion missing 'barcode' query param");
                    return;
                }

                try (var helper = new LibraryHelper(db)) {
                     helper.insertViaLibraries(barcode);
                }

                ctx.redirect("/book/" + barcode, HttpStatus.SEE_OTHER);
            })
            .post("/api/dynamic-insert", ctx -> {
                var barcode = ctx.formParam("barcode");

                if (barcode == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Insertion missing 'barcode' query param");
                    return;
                } else if (barcode.startsWith("clear")) {
                    ctx.redirect("/super", HttpStatus.SEE_OTHER); // clear barcode field in case of misscan
                    return;
                }

                StringJoiner query = new StringJoiner("&");
                List<String> shownBooks = new ArrayList<>();
                List<Book> existingBooks = db.getBooksByCode(barcode);

                if (!existingBooks.isEmpty()) {
                    for (Book existingBook : existingBooks) {
                        shownBooks.add("" + existingBook.id());
                    }
                } else {
                    boolean hasTitle = ctx.formParam("title") != null && !ctx.formParam("title").isBlank();
                    Book viaLibraries;

                    try (var helper = new LibraryHelper(db)) {
                        viaLibraries = helper.getViaLibraries(barcode);
                    }

                    Book toAdd = null;

                    if (viaLibraries == null) {
                        if (hasTitle) {
                            toAdd = new Book(-1, ctx.formParam("title"), emptyToNull(ctx.formParam("author")), emptyToNull(ctx.formParam("url")), barcode, 0, emptyToNull(ctx.formParam("note")));
                        } else {
                            query.add("fillInDetailsManually=true");
                            query.add("existingBarcode=" + barcode);
                        }
                    } else {
                        toAdd = new Book(
                            -1,
                            Objects.requireNonNullElse(emptyToNull(ctx.formParam("title")), viaLibraries.title()),
                            firstNonNull(emptyToNull(ctx.formParam("author")), viaLibraries.author()),
                            firstNonNull(emptyToNull(ctx.formParam("url")), viaLibraries.url()),
                            barcode,
                            viaLibraries.cover(),
                            emptyToNull(ctx.formParam("note"))
                        );
                    }

                    if (toAdd != null) {
                        int bookId = db.insert(toAdd);
                        shownBooks.add("" + bookId);
                    }
                }

                for (String shownBook : shownBooks) {
                    query.add("book=" + shownBook);
                }

                ctx.redirect("/super?" + query, HttpStatus.SEE_OTHER);
            })
            .post("/api/add-tag", ctx -> {
                var bookId = ctx.formParam("book");
                var tag = ctx.formParam("tag");

                if (bookId == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Insertion missing 'book' query param");
                    return;
                } else if (tag == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Insertion missing 'tag' query param");
                    return;
                }

                var returnUrl = ctx.formParam("return");
                if (returnUrl != null && !returnUrl.startsWith("/")) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Malformed return URL");
                    return;
                }

                db.addTag(Integer.parseInt(bookId), tag);

                if (returnUrl != null) {
                    ctx.redirect(returnUrl, HttpStatus.SEE_OTHER);
                } else {
                    ctx.status(HttpStatus.ACCEPTED).result("OK");
                }
            })
            .post("/api/remove-tag", ctx -> {
                var bookId = ctx.formParam("book");
                var tag = ctx.formParam("tag");

                if (bookId == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Deletion missing 'book' query param");
                    return;
                } else if (tag == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Deletion missing 'tag' query param");
                    return;
                }

                var returnUrl = ctx.formParam("return");
                if (returnUrl != null && !returnUrl.startsWith("/")) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Malformed return URL");
                    return;
                }

                db.removeTag(Integer.parseInt(bookId), tag);

                if (returnUrl != null) {
                    ctx.redirect(returnUrl, HttpStatus.SEE_OTHER);
                } else {
                    ctx.status(HttpStatus.ACCEPTED).result("OK");
                }
            })
            .post("/api/set-note", ctx -> {
                var bookId = ctx.formParam("book");
                var note = ctx.formParam("note");

                if (bookId == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Update missing 'book' query param");
                    return;
                } else if (note == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Update missing 'note' query param");
                    return;
                }

                var returnUrl = ctx.formParam("return");
                if (returnUrl != null && !returnUrl.startsWith("/")) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Malformed return URL");
                    return;
                }

                db.updateNote(Integer.parseInt(bookId), note);

                if (returnUrl != null) {
                    ctx.redirect(returnUrl, HttpStatus.SEE_OTHER);
                } else {
                    ctx.status(HttpStatus.ACCEPTED).result("OK");
                }
            })
            .get("/api/qr", ctx -> {
                var code = ctx.queryParam("code");
                var note = Objects.requireNonNullElse(ctx.queryParam("note"), "");
                var image = QrGenerator.generateQrImage(code, note);
                ctx.contentType(ContentType.IMAGE_PNG).result(image);
            })
            .start(config.port);
    }

    private static @Nullable String emptyToNull(@Nullable String a) {
        return a != null && !a.isEmpty() ? a : null;
    }

    private static @Nullable String firstNonNull(@Nullable String a, @Nullable String b) {
        return a != null ? a : b;
    }
}
