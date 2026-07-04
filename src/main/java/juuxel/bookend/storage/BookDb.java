/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package juuxel.bookend.storage;

import juuxel.bookend.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BookDb implements AutoCloseable {
    private static final Logger LOGGER = Logging.logger();
    private final Connection connection;

    private BookDb(Path path) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toString().replace(File.separator, "/"));
        initTables();
    }

    public static BookDb open(Path path) {
        try {
            return new BookDb(path);
        } catch (SQLException e) {
            throw new RuntimeException("Could not open database at path " + path, e);
        }
    }

    private void initTables() throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS Books (id INTEGER PRIMARY KEY, title TEXT, author TEXT, url TEXT, barcode TEXT, cover INTEGER)");
            statement.execute("CREATE TABLE IF NOT EXISTS Covers (id INTEGER PRIMARY KEY, library_ns TEXT, library_cover_id TEXT, media_type TEXT, data BLOB)");
            statement.execute("CREATE TABLE IF NOT EXISTS BookTags (id INTEGER PRIMARY KEY, label TEXT, book INTEGER)");
        }
    }

    public List<Book> getAllBooks() {
        try (var statement = connection.createStatement()) {
            var results = statement.executeQuery("SELECT * FROM Books");
            List<Book> books = new ArrayList<>();

            while (results.next()) {
                books.add(bookFromResultSet(results));
            }

            return books;
        } catch (SQLException e) {
            LOGGER.error("Could not fetch books", e);
            return List.of();
        }
    }

    private Book bookFromResultSet(ResultSet rs) throws SQLException {
        return new Book(rs.getInt("id"), rs.getString("title"), rs.getString("author"), rs.getString("url"), rs.getString("barcode"), rs.getInt("cover"));
    }

    public List<Book> getBooksByCode(String searchTerm) {
        try {
            // Try looking up with ID first, then barcode
            try (var statement = connection.prepareStatement("SELECT * FROM Books WHERE id=?")) {
                statement.setString(1, searchTerm);
                var rs = statement.executeQuery();

                if (rs.next()) {
                    return List.of(bookFromResultSet(rs));
                }
            }

            // Didn't find by ID, check by barcode
            try (var statement = connection.prepareStatement("SELECT * FROM Books WHERE barcode=?")) {
                statement.setString(1, searchTerm);
                var rs = statement.executeQuery();
                List<Book> books = new ArrayList<>();

                while (rs.next()) {
                    books.add(bookFromResultSet(rs));
                }

                return books;
            }
        } catch (SQLException e) {
            LOGGER.error("Could not fetch book by code {}", searchTerm, e);
        }

        return List.of();
    }

    public List<Book> getBooksByTag(String tag) {
        try (var statement = connection.prepareStatement("SELECT Books.id, Books.title, Books.author, Books.url, Books.barcode, Books.cover FROM Books JOIN BookTags ON BookTags.book = Books.id WHERE BookTags.label=?")) {
            statement.setString(1, tag);
            var rs = statement.executeQuery();
            List<Book> books = new ArrayList<>();

            while (rs.next()) {
                books.add(bookFromResultSet(rs));
            }

            return books;
        } catch (SQLException e) {
            LOGGER.error("Could not fetch books by tag {}", tag, e);
        }

        return List.of();
    }

    public @Nullable Cover getCoverById(int id) {
        if (id == 0) return null;

        try {
            try (var statement = connection.prepareStatement("SELECT * FROM Covers WHERE id=?")) {
                statement.setInt(1, id);
                var rs = statement.executeQuery();

                if (rs.next()) {
                    return coverFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not fetch cover by id {}", id, e);
        }

        return null;
    }

    public @Nullable Cover getCoverByLibraryId(String libraryNs, String libraryId) {
        try {
            try (var statement = connection.prepareStatement("SELECT * FROM Covers WHERE library_ns=? AND library_cover_id=?")) {
                statement.setString(1, libraryNs);
                statement.setString(2, libraryId);
                var rs = statement.executeQuery();

                if (rs.next()) {
                    return coverFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not fetch cover by {} cover id {}", libraryNs, libraryId, e);
        }

        return null;
    }

    private Cover coverFromResultSet(ResultSet rs) throws SQLException {
        return new Cover(rs.getInt("id"), rs.getString("library_ns"), rs.getString("library_cover_id"), rs.getString("media_type"), rs.getBytes("data"));
    }

    public List<String> getTags(int bookId) {
        try (var statement = connection.prepareStatement("SELECT label FROM BookTags WHERE book=?")) {
            statement.setInt(1, bookId);
            var rs = statement.executeQuery();
            List<String> tags = new ArrayList<>();

            while (rs.next()) {
                tags.add(rs.getString(1));
            }

            Collections.sort(tags);
            return tags;
        } catch (SQLException e) {
            LOGGER.error("Could not fetch tags for book {}", bookId, e);
        }

        return List.of();
    }

    public void addTag(int bookId, String tag) {
        try (var statement = connection.prepareStatement("INSERT INTO BookTags (book, label) VALUES (?, ?)")) {
            statement.setInt(1, bookId);
            statement.setString(2, tag);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not add tag {} to book {}", tag, bookId, e);
        }
    }

    public void removeTag(int bookId, String tag) {
        try (var statement = connection.prepareStatement("DELETE FROM BookTags WHERE book=? AND label=?")) {
            statement.setInt(1, bookId);
            statement.setString(2, tag);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not delete tag {} from book {}", tag, bookId, e);
        }
    }

    public int insert(Book book) {
        try (var statement = connection.prepareStatement("INSERT INTO Books (title, author, url, barcode, cover) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, book.title());
            statement.setString(2, book.author());
            statement.setString(3, book.url());
            statement.setString(4, book.barcode());
            statement.setInt(5, book.cover());
            statement.executeUpdate();

            ResultSet rs = statement.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Could not insert book {}", book, e);
        }

        return -1;
    }

    public int insert(Cover cover) {
        try (var statement = connection.prepareStatement("INSERT INTO Covers (library_ns, library_cover_id, media_type, data) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, cover.libraryNs());
            statement.setString(2, cover.libraryCoverId());
            statement.setString(3, cover.mediaType());
            statement.setBytes(4, cover.blob());
            statement.executeUpdate();

            ResultSet rs = statement.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Could not insert cover {}", cover, e);
        }

        return -1;
    }

    public void massInsert(List<String> barcodes) {
        try (var statement = connection.prepareStatement("INSERT INTO Books (barcode) VALUES (?)")) {
            for (String barcode : barcodes) {
                statement.setString(1, barcode);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Could not mass insert barcodes", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("Could not close DB connection", e);
        }
    }
}
