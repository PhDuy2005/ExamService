package com.DoAn1.examservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamPaper;
import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionMcOption;
import com.DoAn1.examservice.domain.entity.QuestionTrueFalseStatement;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.exception.StorageException;
import com.DoAn1.examservice.repository.QuestionMcOptionRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.repository.QuestionTrueFalseStatementRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamPaperPdfService {

    private static final String STORAGE_FOLDER = "exam-papers";
    private static final float MARGIN = 48;
    private static final float BODY_FONT_SIZE = 11;
    private static final float SECTION_TITLE_FONT_SIZE = 12;
    private static final float FOOTER_FONT_SIZE = 9;
    private static final float FOOTER_Y = 24;
    private static final float LINE_HEIGHT = 16;
    private static final List<QuestionType> PRINT_SECTION_ORDER = List.of(
            QuestionType.MCQ,
            QuestionType.TFQ,
            QuestionType.SAQ);
    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\$\\$(.+?)\\$\\$|(?<!\\$)\\$(?!\\$)(.+?)(?<!\\$)\\$(?!\\$)",
            Pattern.DOTALL);
    private static final Pattern TEXT_TOKEN_PATTERN = Pattern.compile("\\s+|\\S+");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final QuestionRepository questionRepository;
    private final QuestionMcOptionRepository questionMcOptionRepository;
    private final QuestionTrueFalseStatementRepository questionTrueFalseStatementRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    @Value("${examservice.exam-paper.font-path:C:/Windows/Fonts/arial.ttf}")
    private String fontPath;

    public String generateAndStore(Exam exam, ExamPaper paper) {
        List<PaperQuestionSnapshot> snapshots = deserializeSnapshots(paper.getQuestionSnapshotJson());
        byte[] pdfBytes = generatePdf(exam, paper, snapshots);
        String folder = STORAGE_FOLDER + "/" + exam.getExamUuid();
        String fileName = sanitizeFileName(paper.getPaperCode()) + ".pdf";
        try {
            return fileService.storeBytes(pdfBytes, folder, fileName);
        } catch (IOException ex) {
            throw new StorageException("Failed to store exam paper PDF", ex);
        }
    }

    private byte[] generatePdf(Exam exam, ExamPaper paper, List<PaperQuestionSnapshot> snapshots) {
        List<UUID> questionIds = snapshots.stream().map(PaperQuestionSnapshot::questionUuid).distinct().toList();
        Map<UUID, Question> questionById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getQuestionUuid, question -> question));
        Map<UUID, List<QuestionMcOption>> optionsByQuestion = questionMcOptionRepository
                .findByQuestionUuidInOrderByQuestionUuidAscOptionKeyAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(QuestionMcOption::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion = questionTrueFalseStatementRepository
                .findByQuestionUuidInOrderByQuestionUuidAscStatementOrderAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        QuestionTrueFalseStatement::getQuestionUuid,
                        LinkedHashMap::new,
                        Collectors.toList()));

        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            try (PdfWriter writer = new PdfWriter(document, font)) {
                writer.writeCentered(exam.getExamName(), 18);
                writer.writeCentered("Mã đề: " + paper.getPaperCode(), 14);
                writer.writeLine("Khối/lớp: " + exam.getGradeId(), BODY_FONT_SIZE, 0);
                writer.writeLine("Năm học: " + nullSafe(exam.getSchoolYear()), BODY_FONT_SIZE, 0);
                writer.writeLine("Thời gian làm bài: " + exam.getDurationMinutes() + " phút", BODY_FONT_SIZE, 0);
                writer.writeLine("Tổng điểm: " + exam.getTotalScore(), BODY_FONT_SIZE, 0);
                if (exam.getStartTime() != null) {
                    writer.writeLine("Bắt đầu: " + DATE_TIME_FORMATTER.format(exam.getStartTime()), BODY_FONT_SIZE, 0);
                }
                writer.addVerticalSpace(10);

                for (QuestionType questionType : PRINT_SECTION_ORDER) {
                    List<PaperQuestionSnapshot> sectionQuestions = snapshots.stream()
                            .filter(snapshot -> snapshot.questionType() == questionType)
                            .filter(snapshot -> questionById.containsKey(snapshot.questionUuid()))
                            .sorted(Comparator.comparing(PaperQuestionSnapshot::questionOrder))
                            .toList();
                    if (sectionQuestions.isEmpty()) {
                        continue;
                    }

                    writer.writeSectionTitle(getSectionTitle(questionType));
                    writer.addVerticalSpace(6);
                    for (PaperQuestionSnapshot snapshot : sectionQuestions) {
                        writeQuestion(writer, snapshot, questionById, optionsByQuestion, statementsByQuestion);
                    }
                }
            }

            addPageFooters(document, font, paper.getPaperCode());
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new StorageException("Failed to generate exam paper PDF", ex);
        }
    }

    private void writeQuestion(
            PdfWriter writer,
            PaperQuestionSnapshot snapshot,
            Map<UUID, Question> questionById,
            Map<UUID, List<QuestionMcOption>> optionsByQuestion,
            Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion) throws IOException {
        Question question = questionById.get(snapshot.questionUuid());
        if (question == null) {
            return;
        }

        writer.writeLine(
                "C\u00e2u " + snapshot.sectionQuestionNumber() + " (" + formatScore(snapshot.score()) + " \u0111i\u1ec3m): "
                        + question.getQuestionContent(),
                BODY_FONT_SIZE,
                0);
        writer.drawStorageImage(question.getImagePath());

        if (snapshot.questionType() == QuestionType.MCQ) {
            for (QuestionMcOption option : optionsByQuestion.getOrDefault(snapshot.questionUuid(), List.of())) {
                writer.writeLine(option.getOptionKey() + ". " + option.getOptionContent(), BODY_FONT_SIZE, 18);
            }
        } else if (snapshot.questionType() == QuestionType.TFQ) {
            for (QuestionTrueFalseStatement statement : statementsByQuestion.getOrDefault(snapshot.questionUuid(), List.of())) {
                writer.writeLine(statement.getStatementOrder() + ") " + statement.getStatementContent(), BODY_FONT_SIZE, 18);
            }
        } else {
            writer.writeLine("Tr\u1ea3 l\u1eddi: ________________________________________________", BODY_FONT_SIZE, 18);
        }
        writer.addVerticalSpace(8);
    }

    private String getSectionTitle(QuestionType questionType) {
        return switch (questionType) {
            case MCQ -> "Ph\u1ea7n 1 - Th\u00ed sinh ch\u1ecdn m\u1ed9t trong b\u1ed1n \u0111\u00e1p \u00e1n";
            case TFQ -> "Ph\u1ea7n 2 - Th\u00ed sinh ch\u1ecdn \u0111\u00fang ho\u1eb7c sai cho m\u1ed7i m\u1ec7nh \u0111\u1ec1 trong c\u00e2u h\u1ecfi";
            case SAQ -> "Ph\u1ea7n 3 - Th\u00ed sinh tr\u1ea3 l\u1eddi c\u00e1c c\u00e2u h\u1ecfi sau";
        };
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        Path configuredFont = Path.of(fontPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(configuredFont)) {
            throw new StorageException("Exam paper font file not found: " + configuredFont);
        }
        return PDType0Font.load(document, configuredFont.toFile());
    }

    private void addPageFooters(PDDocument document, PDFont font, String paperCode) throws IOException {
        int totalPages = document.getNumberOfPages();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            String leftText = "M\u00e3 \u0111\u1ec1: " + paperCode;
            String rightText = "Trang " + (pageIndex + 1) + "/" + totalPages;
            float rightTextWidth = font.getStringWidth(rightText) / 1000 * FOOTER_FONT_SIZE;
            float rightX = page.getMediaBox().getWidth() - MARGIN - rightTextWidth;

            try (PDPageContentStream footerStream = new PDPageContentStream(
                    document,
                    page,
                    AppendMode.APPEND,
                    true,
                    true)) {
                writeFooterText(footerStream, font, leftText, MARGIN);
                writeFooterText(footerStream, font, rightText, rightX);
            }
        }
    }

    private void writeFooterText(PDPageContentStream contentStream, PDFont font, String text, float x)
            throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, FOOTER_FONT_SIZE);
        contentStream.newLineAtOffset(x, FOOTER_Y);
        contentStream.showText(text);
        contentStream.endText();
    }

    private List<PaperQuestionSnapshot> deserializeSnapshots(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<PaperQuestionSnapshot>>() {
            });
        } catch (IOException ex) {
            throw new StorageException("Failed to read exam paper snapshot for PDF generation", ex);
        }
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String formatScore(BigDecimal score) {
        return score == null ? "0" : score.stripTrailingZeros().toPlainString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record PaperQuestionSnapshot(
            Integer questionOrder,
            Integer sectionQuestionNumber,
            UUID questionUuid,
            QuestionType questionType,
            BigDecimal score,
            Boolean fromQuestionGroup,
            UUID groupUuid,
            String groupName) {
    }

    private final class PdfWriter implements AutoCloseable {

        private final PDDocument document;
        private final PDFont font;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float y;

        private PdfWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void writeCentered(String text, float fontSize) throws IOException {
            ensureSpace(LINE_HEIGHT * 2);
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;
            writeText(text, fontSize, Math.max(MARGIN, (page.getMediaBox().getWidth() - textWidth) / 2));
        }

        private void writeLine(String text, float fontSize, float indent) throws IOException {
            float availableWidth = page.getMediaBox().getWidth() - (MARGIN * 2) - indent;
            for (String paragraph : text.replace("\r", "").split("\n", -1)) {
                writeInlineParagraph(paragraph, fontSize, MARGIN + indent, availableWidth);
            }
        }

        private void writeSectionTitle(String title) throws IOException {
            ensureSpace(LINE_HEIGHT * 4);
            writeLine(title, SECTION_TITLE_FONT_SIZE, 0);
        }

        private void writeText(String text, float fontSize, float x) throws IOException {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
            y -= LINE_HEIGHT;
        }

        private void writeInlineParagraph(String paragraph, float fontSize, float startX, float maxWidth)
                throws IOException {
            List<InlineItem> items = parseInlineItems(paragraph, fontSize);
            if (items.isEmpty()) {
                ensureSpace(LINE_HEIGHT);
                y -= LINE_HEIGHT;
                return;
            }

            List<InlineItem> line = new ArrayList<>();
            float lineWidth = 0;
            for (InlineItem item : items) {
                float gap = line.isEmpty() ? 0 : item.leadingSpaceWidth();
                if (!line.isEmpty() && lineWidth + gap + item.width() > maxWidth) {
                    drawInlineLine(line, startX);
                    line = new ArrayList<>();
                    lineWidth = 0;
                }
                line.add(item);
                lineWidth += (line.size() == 1 ? 0 : item.leadingSpaceWidth()) + item.width();
            }
            if (!line.isEmpty()) {
                drawInlineLine(line, startX);
            }
        }

        private List<InlineItem> parseInlineItems(String paragraph, float fontSize) throws IOException {
            List<InlineItem> items = new ArrayList<>();
            Matcher matcher = LATEX_PATTERN.matcher(paragraph);
            int cursor = 0;
            boolean hasLeadingSpace = false;
            while (matcher.find()) {
                hasLeadingSpace = addTextItems(
                        items,
                        paragraph.substring(cursor, matcher.start()),
                        fontSize,
                        hasLeadingSpace);
                String latex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                try {
                    BufferedImage formulaImage = renderFormula(latex, fontSize);
                    items.add(new InlineItem(
                            null,
                            formulaImage,
                            formulaImage.getWidth(),
                            fontSize,
                            hasLeadingSpace ? getSpaceWidth(fontSize) : 0));
                    hasLeadingSpace = false;
                } catch (RuntimeException ex) {
                    hasLeadingSpace = addTextItems(items, matcher.group(), fontSize, hasLeadingSpace);
                }
                cursor = matcher.end();
            }
            addTextItems(items, paragraph.substring(cursor), fontSize, hasLeadingSpace);
            return items;
        }

        private boolean addTextItems(
                List<InlineItem> items,
                String text,
                float fontSize,
                boolean hasLeadingSpace) throws IOException {
            Matcher tokenMatcher = TEXT_TOKEN_PATTERN.matcher(text);
            while (tokenMatcher.find()) {
                String token = tokenMatcher.group();
                if (token.isBlank()) {
                    hasLeadingSpace = true;
                } else {
                    items.add(new InlineItem(
                            token,
                            null,
                            font.getStringWidth(token) / 1000 * fontSize,
                            fontSize,
                            hasLeadingSpace ? getSpaceWidth(fontSize) : 0));
                    hasLeadingSpace = false;
                }
            }
            return hasLeadingSpace;
        }

        private float getSpaceWidth(float fontSize) throws IOException {
            return font.getStringWidth(" ") / 1000 * fontSize;
        }

        private BufferedImage renderFormula(String latex, float fontSize) {
            TeXIcon icon = new TeXFormula(latex)
                    .createTeXIcon(TeXConstants.STYLE_DISPLAY, Math.round(fontSize + 1));
            icon.setInsets(new Insets(1, 1, 1, 1));
            BufferedImage image = new BufferedImage(
                    Math.max(1, icon.getIconWidth()),
                    Math.max(1, icon.getIconHeight()),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.BLACK);
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            return image;
        }

        private void drawInlineLine(List<InlineItem> items, float startX) throws IOException {
            float lineHeight = items.stream()
                    .filter(InlineItem::isFormula)
                    .map(item -> item.formulaImage().getHeight() + 4f)
                    .max(Float::compare)
                    .orElse(LINE_HEIGHT);
            lineHeight = Math.max(LINE_HEIGHT, lineHeight);
            ensureSpace(lineHeight);

            float baselineY = y - Math.max(0, (lineHeight - LINE_HEIGHT) / 2);
            float x = startX;
            for (InlineItem item : items) {
                if (x > startX) {
                    x += item.leadingSpaceWidth();
                }
                if (item.isFormula()) {
                    PDImageXObject formulaImage = LosslessFactory.createFromImage(document, item.formulaImage());
                    contentStream.drawImage(
                            formulaImage,
                            x,
                            baselineY - item.formulaImage().getHeight() + 3,
                            item.formulaImage().getWidth(),
                            item.formulaImage().getHeight());
                } else {
                    contentStream.beginText();
                    contentStream.setFont(font, item.fontSize());
                    contentStream.newLineAtOffset(x, baselineY);
                    contentStream.showText(item.text());
                    contentStream.endText();
                }
                x += item.width();
            }
            y -= lineHeight;
        }

        private void drawStorageImage(String imagePath) throws IOException {
            if (!StringUtils.hasText(imagePath) || !imagePath.startsWith("/storage/")) {
                return;
            }
            Path path;
            try {
                path = fileService.resolveStorageUrl(imagePath);
            } catch (StorageException ex) {
                return;
            }
            if (!Files.isRegularFile(path)) {
                return;
            }

            PDImageXObject image = PDImageXObject.createFromFileByContent(path.toFile(), document);
            float maxWidth = page.getMediaBox().getWidth() - (MARGIN * 2);
            float width = Math.min(maxWidth, image.getWidth());
            float height = image.getHeight() * (width / image.getWidth());
            float maxHeight = 260;
            if (height > maxHeight) {
                width *= maxHeight / height;
                height = maxHeight;
            }
            ensureSpace(height + 8);
            contentStream.drawImage(image, MARGIN, y - height, width, height);
            y -= height + 8;
        }

        private void addVerticalSpace(float height) throws IOException {
            ensureSpace(height);
            y -= height;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
        }

        private record InlineItem(
                String text,
                BufferedImage formulaImage,
                float width,
                float fontSize,
                float leadingSpaceWidth) {

            private boolean isFormula() {
                return formulaImage != null;
            }
        }
    }
}
