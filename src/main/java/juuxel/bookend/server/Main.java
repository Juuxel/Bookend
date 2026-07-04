/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.server;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import juuxel.bookend.config.Config;
import juuxel.bookend.server.libraryapi.LibraryHelper;
import juuxel.bookend.storage.Book;
import juuxel.bookend.storage.BookDb;
import juuxel.bookend.template.TemplateManager;
import juuxel.bookend.util.Logging;
import org.slf4j.Logger;

import java.nio.file.Path;
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
            .get("/book/{code}", ctx -> {
                List<Book> books = db.getBooksByCode(ctx.pathParam("code"));

                switch (books.size()) {
                    case 0 -> ctx.status(HttpStatus.NOT_FOUND).result("Didn't find it :(");
                    case 1 -> {
                        ctx.html(templateManager.loadTemplate("ViewBook", Map.of("book", books.getFirst())));
                    }
                    default -> {
                        ctx.html(templateManager.loadTemplate("ViewBookDisambiguation", Map.of("books", books)));
                    }
                }
            })
            .get("/api/all", ctx -> {
                var joiner = new StringJoiner(
                    "</tr><tr>",
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <body>
                    <table>
                    <tr>
                    <th>ID</th>
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
                    joiner.add(
                        """
                        <td>%d</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        <td>%s</td>
                        """.formatted(book.id(), book.title(), book.author(), book.url(), book.barcode())
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

                int bookId = db.insert(new Book(-1, title, author, url, barcode, 0));
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
            .start(config.port);
    }
}
