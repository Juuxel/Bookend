package juuxel.bookend.storage;

import org.jspecify.annotations.Nullable;

public record Book(int id, @Nullable String title, @Nullable String author, @Nullable String url, @Nullable String barcode) {
}
