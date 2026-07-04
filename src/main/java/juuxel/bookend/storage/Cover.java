package juuxel.bookend.storage;

import org.jspecify.annotations.Nullable;

public record Cover(int id, @Nullable String libraryNs, @Nullable String libraryCoverId, String mediaType, byte[] blob) {
}
