package juuxel.bookend.storage;

import juuxel.bookend.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class BookDb implements AutoCloseable {
    private static final Logger LOGGER = Logging.logger();
    private final Connection connection;

    private BookDb(Path path) throws SQLException {
        boolean newCopy = !Files.exists(path);
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toString().replace(File.separator, "/"));

        if (newCopy) {
            initTables();
        }
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
            statement.execute("CREATE TABLE Books (id INTEGER PRIMARY KEY, title TEXT, author TEXT, url TEXT, barcode TEXT)");
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
        return new Book(rs.getInt("id"), rs.getString("title"), rs.getString("author"), rs.getString("url"), rs.getString("barcode"));
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

    public int insert(Book book) {
        try (var statement = connection.prepareStatement("INSERT INTO Books (title, author, url, barcode) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, book.title());
            statement.setString(2, book.author());
            statement.setString(3, book.url());
            statement.setString(4, book.barcode());
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
