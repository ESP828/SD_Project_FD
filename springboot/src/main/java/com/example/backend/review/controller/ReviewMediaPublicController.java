package com.example.backend.review.controller;

import com.example.backend.review.service.ReviewMediaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/reviews/media")
public class ReviewMediaPublicController {

    private final ReviewMediaService reviewMediaService;

    public ReviewMediaPublicController(ReviewMediaService reviewMediaService) {
        this.reviewMediaService = reviewMediaService;
    }

    @GetMapping("/{reviewMediaId}")
    public void getMedia(
            @PathVariable Long reviewMediaId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestParam(defaultValue = "false") boolean download,
            HttpServletResponse response
    ) throws IOException {
        ReviewMediaService.MediaDownload media = reviewMediaService.getMedia(
                reviewMediaId,
                range,
                download
        );

        String etag = "\"review-media-" + media.reviewMediaId() + "-" + media.totalSize() + "\"";
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, no-transform");
        response.setHeader(HttpHeaders.ETAG, etag);
        response.setHeader(HttpHeaders.VARY, HttpHeaders.RANGE);

        if (etag.equals(ifNoneMatch)) {
            response.setStatus(HttpStatus.NOT_MODIFIED.value());
            return;
        }

        response.setStatus(media.partial()
                ? HttpStatus.PARTIAL_CONTENT.value()
                : HttpStatus.OK.value());
        response.setContentType(resolveMediaType(media.mimeType()).toString());
        response.setContentLengthLong(media.contentLength());
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                (media.download() ? ContentDisposition.attachment() : ContentDisposition.inline())
                        .filename(media.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString()
        );
        if (media.partial()) {
            response.setHeader(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + media.start() + "-" + media.end() + "/" + media.totalSize()
            );
        }

        OutputStream outputStream = response.getOutputStream();
        reviewMediaService.stream(
                media.reviewMediaId(),
                media.start(),
                media.contentLength(),
                outputStream
        );
        outputStream.flush();
    }

    private MediaType resolveMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
