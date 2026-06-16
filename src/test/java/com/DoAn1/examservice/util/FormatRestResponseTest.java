package com.DoAn1.examservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.DoAn1.examservice.domain.response.RestResponse;
import com.DoAn1.examservice.domain.responseDTO.omr.ResExamPaperDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResExamPaperQuestionDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrScoringJobDTO;
import com.DoAn1.examservice.util.annotation.ApiMessage;

class FormatRestResponseTest {

    private static final String STORAGE_ROOT = "D:/DoAn/DoAn1_storage";

    private final FormatRestResponse advice = new FormatRestResponse(STORAGE_ROOT, "/storage/");

    @Test
    void beforeBodyWriteConvertsNestedStorageUrlsToAbsolutePaths() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/v1/omr/exam-papers");

        ResExamPaperDTO body = ResExamPaperDTO.builder()
                .pdfUrl("http://localhost:8080/storage/omr/papers/paper-a.pdf")
                .questions(List.of(ResExamPaperQuestionDTO.builder()
                        .imagePath("/storage/questions/question-1.png")
                        .build()))
                .build();

        Object result = advice.beforeBodyWrite(
                body,
                returnType(),
                MediaType.APPLICATION_JSON,
                Object.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isInstanceOf(RestResponse.class);
        assertThat(body.getPdfUrl()).isEqualTo(storagePath("omr/papers/paper-a.pdf"));
        assertThat(body.getRelativePath()).isEqualTo("omr/papers/paper-a.pdf");
        assertThat(body.getQuestions().get(0).getImagePath())
                .isEqualTo(storagePath("questions/question-1.png"));
        assertThat(body.getQuestions().get(0).getRelativePath()).isEqualTo("questions/question-1.png");
    }

    @Test
    void beforeBodyWriteConvertsConfiguredUrlPrefixesToRelativePaths() throws Exception {
        FormatRestResponse customAdvice = new FormatRestResponse(
                STORAGE_ROOT,
                "http://scoring.local/files/,/storage/");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/v1/omr/scoring-jobs/job-1");

        ResOmrScoringJobDTO body = ResOmrScoringJobDTO.builder()
                .rawImageUrl("http://scoring.local/files/omr/raw/file.pdf")
                .scoredImageUrl("http://scoring.local/files/omr/scored/page-1.png?token=abc")
                .build();

        Object result = customAdvice.beforeBodyWrite(
                body,
                returnType(),
                MediaType.APPLICATION_JSON,
                Object.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isInstanceOf(RestResponse.class);
        assertThat(body.getRawImageUrl()).isEqualTo(storagePath("omr/raw/file.pdf"));
        assertThat(body.getRawImageRelativePath()).isEqualTo("omr/raw/file.pdf");
        assertThat(body.getScoredImageUrl()).isEqualTo(storagePath("omr/scored/page-1.png"));
        assertThat(body.getScoredImageRelativePath()).isEqualTo("omr/scored/page-1.png");
    }

    private MethodParameter returnType() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("response");
        return new MethodParameter(method, -1);
    }

    private String storagePath(String relativePath) {
        return Path.of(STORAGE_ROOT).toAbsolutePath().normalize().resolve(relativePath).normalize().toString();
    }

    private static class TestController {

        @ApiMessage("Test response")
        ResExamPaperDTO response() {
            return null;
        }
    }
}
