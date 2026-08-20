package com.example.backend.review.service;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.dto.response.ReviewMediaResponse;
import com.example.backend.review.repository.ReviewMediaRepository;
import com.example.backend.review.repository.ReviewMediaRepository.ReviewMediaFileReference;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewMediaService {

    public static final int MAX_MEDIA_COUNT = 10;
    public static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    public static final int MAX_VIDEO_BYTES = 100 * 1024 * 1024;

    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;
    private static final int MEDIA_DB_READ_CHUNK_BYTES = 1024 * 1024;
    private static final int INITIAL_MEDIA_RANGE_BYTES = 8 * 1024 * 1024;
    private static final int STREAM_MEDIA_RANGE_BYTES = 8 * 1024 * 1024;
    private static final int SUFFIX_MEDIA_RANGE_BYTES = 4 * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "tif", "tiff", "avif", "heic", "heif"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "ogv", "m4v", "mov", "mkv",
            "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "3g2"
    );
    private static final Map<String, String> MEDIA_MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("avif", "image/avif"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("mpg", "video/mpeg"),
            Map.entry("mpeg", "video/mpeg"),
            Map.entry("3gp", "video/3gpp"),
            Map.entry("3g2", "video/3gpp2")
    );

    private final ReviewRepository reviewRepository;
    private final ReviewMediaRepository reviewMediaRepository;

    public ReviewMediaService(
            ReviewRepository reviewRepository,
            ReviewMediaRepository reviewMediaRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewMediaRepository = reviewMediaRepository;
    }

    public ReviewMediaResponse upload(
            Long reviewId,
            Long accountId,
            String encodedOriginalName,
            String declaredContentType,
            InputStream mediaData,
            long declaredContentLength
    ) {
        requireOwnedReview(reviewId, accountId);
        if (reviewMediaRepository.countByReviewId(reviewId) >= MAX_MEDIA_COUNT) {
            throw invalidInput("리뷰에는 사진과 동영상을 합해 최대 10개까지 첨부할 수 있습니다.");
        }

        String originalName = normalizeOriginalName(encodedOriginalName);
        MediaTypeInfo mediaType = resolveMediaType(originalName, declaredContentType);
        long maximumBytes = mediaType.image() ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        validateDeclaredLength(declaredContentLength, maximumBytes, mediaType.image());

        Path stagedMedia = stageMedia(mediaData, maximumBytes, mediaType.image());
        try {
            validateMediaSignature(originalName, stagedMedia);
            // 업로드 도중 다른 요청이 들어온 경우에도 최대 개수를 다시 확인한다.
            requireOwnedReview(reviewId, accountId);
            if (reviewMediaRepository.countByReviewId(reviewId) >= MAX_MEDIA_COUNT) {
                throw invalidInput("리뷰에는 사진과 동영상을 합해 최대 10개까지 첨부할 수 있습니다.");
            }
            int displayOrder = reviewMediaRepository.nextDisplayOrder(reviewId);
            try (InputStream inputStream = Files.newInputStream(stagedMedia)) {
                Long reviewMediaId = reviewMediaRepository.save(
                        reviewId,
                        mediaType.databaseType(),
                        mediaType.mimeType(),
                        originalName,
                        Files.size(stagedMedia),
                        displayOrder,
                        inputStream
                );
                return toResponse(
                        reviewMediaId,
                        mediaType.databaseType(),
                        mediaType.mimeType(),
                        originalName,
                        Files.size(stagedMedia),
                        displayOrder
                );
            } catch (IOException exception) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "리뷰 첨부파일을 저장하지 못했습니다."
                );
            }
        } finally {
            deleteQuietly(stagedMedia);
        }
    }

    @Transactional
    public void delete(Long reviewId, Long reviewMediaId, Long accountId) {
        requireOwnedReview(reviewId, accountId);
        ReviewMediaFileReference media = reviewMediaRepository.findFile(reviewMediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
        if (!reviewId.equals(media.reviewId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
        }
        if (reviewMediaRepository.delete(reviewId, reviewMediaId) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public MediaDownload getMedia(
            Long reviewMediaId,
            String rangeHeader,
            boolean download
    ) {
        ReviewMediaFileReference media = reviewMediaRepository.findFile(reviewMediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
        if (media.fileSize() < 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "첨부파일 데이터가 없습니다.");
        }
        ByteRange range = resolveByteRange(rangeHeader, media.fileSize());
        return new MediaDownload(
                media.reviewMediaId(),
                media.mimeType(),
                media.originalName(),
                media.fileSize(),
                range.start(),
                range.end(),
                range.partial(),
                download,
                range.end() - range.start() + 1
        );
    }

    public void stream(
            Long reviewMediaId,
            long zeroBasedStart,
            long contentLength,
            OutputStream outputStream
    ) throws IOException {
        long written = 0;
        while (written < contentLength) {
            int requestedLength = (int) Math.min(
                    MEDIA_DB_READ_CHUNK_BYTES,
                    contentLength - written
            );
            byte[] chunk = reviewMediaRepository.readChunk(
                    reviewMediaId,
                    zeroBasedStart + written,
                    requestedLength
            );
            if (chunk.length == 0) {
                break;
            }
            outputStream.write(chunk);
            written += chunk.length;
            if (chunk.length != requestedLength) {
                break;
            }
        }
        if (written != contentLength) {
            throw new IOException(
                    "리뷰 첨부파일 데이터를 모두 전송하지 못했습니다. expected="
                            + contentLength + ", actual=" + written
            );
        }
    }

    private Review requireOwnedReview(Long reviewId, Long accountId) {
        if (reviewId == null || reviewId < 1 || accountId == null || accountId < 1) {
            throw invalidInput("리뷰 정보를 확인해 주세요.");
        }
        return reviewRepository.findActiveOwnedReview(reviewId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private String normalizeOriginalName(String encodedOriginalName) {
        if (encodedOriginalName == null || encodedOriginalName.isBlank()) {
            throw invalidInput("첨부파일 이름이 없습니다.");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(encodedOriginalName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidInput("첨부파일 이름 형식이 올바르지 않습니다.");
        }
        decoded = decoded.replace('\\', '/');
        int separator = decoded.lastIndexOf('/');
        String fileName = separator >= 0 ? decoded.substring(separator + 1) : decoded;
        fileName = fileName.replaceAll("[\\p{Cntrl}]", "_").strip();
        if (fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)) {
            throw invalidInput("첨부파일 이름이 올바르지 않습니다.");
        }
        return fileName.length() > MAX_ORIGINAL_NAME_LENGTH
                ? fileName.substring(0, MAX_ORIGINAL_NAME_LENGTH)
                : fileName;
    }

    private MediaTypeInfo resolveMediaType(String originalName, String declaredContentType) {
        String extension = findExtension(originalName);
        String mimeType = MEDIA_MIME_TYPES.get(extension);
        if (mimeType == null) {
            throw invalidInput("지원하지 않는 파일 형식입니다. 사진 또는 동영상 파일을 선택해 주세요.");
        }
        boolean image = IMAGE_EXTENSIONS.contains(extension);
        if (!image && !VIDEO_EXTENSIONS.contains(extension)) {
            throw invalidInput("지원하지 않는 첨부파일 형식입니다.");
        }
        String normalizedDeclaredType = normalizeContentType(declaredContentType);
        if (normalizedDeclaredType != null && !"application/octet-stream".equals(normalizedDeclaredType)) {
            String expectedPrefix = image ? "image/" : "video/";
            if (!normalizedDeclaredType.startsWith(expectedPrefix)) {
                throw invalidInput("파일의 Content-Type과 실제 미디어 형식이 일치하지 않습니다.");
            }
        }
        return new MediaTypeInfo(image, image ? "IMAGE" : "VIDEO_LINK", mimeType);
    }

    private Path stageMedia(InputStream mediaData, long maximumBytes, boolean image) {
        if (mediaData == null) {
            throw invalidInput("비어 있는 파일은 첨부할 수 없습니다.");
        }
        Path stagedMedia = null;
        long fileSize = 0;
        try {
            stagedMedia = Files.createTempFile("fooduck-review-media-", ".upload");
            try (OutputStream outputStream = Files.newOutputStream(stagedMedia)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = mediaData.read(buffer)) != -1) {
                    if (fileSize + read > maximumBytes) {
                        throw mediaTooLarge(image);
                    }
                    outputStream.write(buffer, 0, read);
                    fileSize += read;
                }
            }
            if (fileSize == 0) {
                throw invalidInput("비어 있는 파일은 첨부할 수 없습니다.");
            }
            return stagedMedia;
        } catch (BusinessException exception) {
            deleteQuietly(stagedMedia);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(stagedMedia);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "리뷰 첨부파일을 서버에서 처리하지 못했습니다.");
        }
    }

    private void validateDeclaredLength(long declaredContentLength, long maximumBytes, boolean image) {
        if (declaredContentLength == 0) {
            throw invalidInput("비어 있는 파일은 첨부할 수 없습니다.");
        }
        if (declaredContentLength > maximumBytes) {
            throw mediaTooLarge(image);
        }
    }

    private void validateMediaSignature(String originalName, Path stagedMedia) {
        byte[] signature;
        try (InputStream inputStream = Files.newInputStream(stagedMedia)) {
            signature = inputStream.readNBytes(64);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "첨부파일 형식을 확인하지 못했습니다.");
        }
        if (!matchesFileSignature(findExtension(originalName), signature)) {
            throw invalidInput("파일 확장자와 실제 파일 형식이 일치하지 않습니다.");
        }
    }

    private String findExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) {
            throw invalidInput("첨부파일 확장자를 확인해 주세요.");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return normalized.strip().toLowerCase(Locale.ROOT);
    }

    private boolean matchesFileSignature(String extension, byte[] data) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(data, 0xff, 0xd8, 0xff);
            case "png" -> startsWith(data, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "gif" -> asciiEquals(data, 0, "GIF87a") || asciiEquals(data, 0, "GIF89a");
            case "webp" -> asciiEquals(data, 0, "RIFF") && asciiEquals(data, 8, "WEBP");
            case "bmp" -> asciiEquals(data, 0, "BM");
            case "tif", "tiff" -> startsWith(data, 0x49, 0x49, 0x2a, 0x00)
                    || startsWith(data, 0x4d, 0x4d, 0x00, 0x2a);
            case "avif" -> isIsoBaseMediaFile(data) && containsIsoBrand(data, "avif", "avis");
            case "heic", "heif" -> isIsoBaseMediaFile(data)
                    && containsIsoBrand(data, "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");
            case "mp4", "m4v", "3gp", "3g2" -> isIsoBaseMediaFile(data) && !isIsoImageFile(data);
            case "mov" -> (isIsoBaseMediaFile(data) && !isIsoImageFile(data))
                    || asciiEquals(data, 4, "moov") || asciiEquals(data, 4, "mdat");
            case "webm", "mkv" -> startsWith(data, 0x1a, 0x45, 0xdf, 0xa3);
            case "avi" -> asciiEquals(data, 0, "RIFF") && asciiEquals(data, 8, "AVI ");
            case "ogv" -> asciiEquals(data, 0, "OggS");
            case "wmv" -> startsWith(data, 0x30, 0x26, 0xb2, 0x75, 0x8e, 0x66, 0xcf, 0x11,
                    0xa6, 0xd9, 0x00, 0xaa, 0x00, 0x62, 0xce, 0x6c);
            case "flv" -> asciiEquals(data, 0, "FLV");
            case "mpg", "mpeg" -> startsWith(data, 0x00, 0x00, 0x01, 0xba)
                    || startsWith(data, 0x00, 0x00, 0x01, 0xb3);
            default -> false;
        };
    }

    private boolean isIsoBaseMediaFile(byte[] data) {
        return asciiEquals(data, 4, "ftyp");
    }

    private boolean isIsoImageFile(byte[] data) {
        return containsIsoBrand(data, "avif", "avis", "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");
    }

    private boolean containsIsoBrand(byte[] data, String... brands) {
        int maximum = Math.min(data.length, 64);
        for (String brand : brands) {
            byte[] expected = brand.getBytes(StandardCharsets.US_ASCII);
            for (int offset = 8; offset + expected.length <= maximum; offset++) {
                boolean matched = true;
                for (int index = 0; index < expected.length; index++) {
                    if (data[offset + index] != expected[index]) {
                        matched = false;
                        break;
                    }
                }
                if (matched) return true;
            }
        }
        return false;
    }

    private boolean asciiEquals(byte[] data, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || data.length < offset + bytes.length) return false;
        for (int index = 0; index < bytes.length; index++) {
            if (data[offset + index] != bytes[index]) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((data[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private ByteRange resolveByteRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return new ByteRange(0, totalSize - 1, false);
        }
        if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) {
            throw rangeNotSatisfiable();
        }
        String value = rangeHeader.substring("bytes=".length()).strip();
        int dash = value.indexOf('-');
        if (dash < 0) throw rangeNotSatisfiable();
        String startValue = value.substring(0, dash).strip();
        String endValue = value.substring(dash + 1).strip();
        try {
            long start;
            long end;
            if (startValue.isEmpty()) {
                long suffixLength = Long.parseLong(endValue);
                if (suffixLength < 1) throw rangeNotSatisfiable();
                suffixLength = Math.min(suffixLength, SUFFIX_MEDIA_RANGE_BYTES);
                start = Math.max(0, totalSize - suffixLength);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(startValue);
                if (start < 0 || start >= totalSize) throw rangeNotSatisfiable();
                int maximumRangeBytes = start == 0 ? INITIAL_MEDIA_RANGE_BYTES : STREAM_MEDIA_RANGE_BYTES;
                long maximumEnd = Math.min(totalSize - 1, start + maximumRangeBytes - 1L);
                end = endValue.isEmpty() ? maximumEnd : Math.min(Long.parseLong(endValue), maximumEnd);
                if (end < start) throw rangeNotSatisfiable();
            }
            return new ByteRange(start, end, true);
        } catch (NumberFormatException exception) {
            throw rangeNotSatisfiable();
        }
    }

    private ResponseStatusException rangeNotSatisfiable() {
        return new ResponseStatusException(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "요청한 첨부파일 구간을 제공할 수 없습니다."
        );
    }

    private ReviewMediaResponse toResponse(
            Long reviewMediaId,
            String mediaType,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
        return new ReviewMediaResponse(
                reviewMediaId,
                mediaType,
                "/api/public/reviews/media/" + reviewMediaId,
                mimeType,
                originalName,
                fileSize,
                displayOrder
        );
    }

    private BusinessException mediaTooLarge(boolean image) {
        return invalidInput(
                image
                        ? "사진은 한 파일당 20MB 이하만 첨부할 수 있습니다."
                        : "동영상은 한 파일당 100MB 이하만 첨부할 수 있습니다."
        );
    }

    private BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record MediaTypeInfo(boolean image, String databaseType, String mimeType) {
    }

    private record ByteRange(long start, long end, boolean partial) {
    }

    public record MediaDownload(
            Long reviewMediaId,
            String mimeType,
            String originalName,
            long totalSize,
            long start,
            long end,
            boolean partial,
            boolean download,
            long contentLength
    ) {
    }
}
