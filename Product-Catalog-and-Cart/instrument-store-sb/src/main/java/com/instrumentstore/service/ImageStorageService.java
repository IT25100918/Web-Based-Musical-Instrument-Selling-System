package com.instrumentstore.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Saves admin-uploaded image files (product photos, category icons) to the
 * ./uploads/ folder and returns the URL path to store in the DB. Shared by
 * ProductCatalogShoppingCartController (products and categories both) so there's one place
 * that decides how files are named and stored, instead of two copies of
 * the same logic drifting apart over time.
 *
 * Only real raster image types are accepted. Files are served back from the
 * same origin as the store (/uploads/**), so accepting .html/.svg/.jsp etc.
 * would let an uploader host scripts on the site (stored XSS).
 */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /**
     * @throws IllegalArgumentException if the file is not an allowed image type
     */
    public String storeImageFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        int dot = originalName.lastIndexOf('.');
        String extension = dot >= 0 ? originalName.substring(dot).toLowerCase(Locale.ROOT) : "";

        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension) || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only JPG, PNG, GIF or WebP image files can be uploaded.");
        }

        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // The name is a random UUID plus a whitelisted extension - nothing from the client
        // (such as path separators or ".." in the original name) ever reaches the file system.
        String uniqueName = UUID.randomUUID() + extension;

        Path destination = uploadDir.resolve(uniqueName);
        file.transferTo(destination);

        return "/uploads/" + uniqueName;
    }
}
