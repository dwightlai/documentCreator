package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OcrMaterialIntegrationTest {
    private static final String TENANT = "tenant-ocr";
    private static final AtomicReference<String> RECEIVED = new AtomicReference<>();
    private static final HttpServer OCR = startOcrServer();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void ocrEndpoint(DynamicPropertyRegistry registry) {
        registry.add(
                "platform.ocr.endpoint",
                () -> "http://127.0.0.1:" + OCR.getAddress().getPort() + "/ocr");
    }

    @AfterAll
    static void stop() {
        OCR.stop(0);
    }

    @Test
    void scannedPdfMaterialIsRecognizedThroughOcrService() throws Exception {
        String body = mockMvc.perform(
                        post("/api/v1/writing-tasks")
                                .header("X-Tenant-Id", TENANT)
                                .header("X-User-Id", "writer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"taskName":"扫描件材料","documentTypeCode":"报告","documentCategory":"法定公文",
                     "writingTopic":"工作情况","expectedLengthMin":500,"expectedLengthMax":1000}
                    """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String taskId = objectMapper.readTree(body).path("data").path("id").asText();
        byte[] scanned = imageOnlyPdf();

        String uploaded = mockMvc.perform(multipart("/api/v1/writing-tasks/{id}/materials/upload", taskId)
                        .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", scanned))
                        .header("X-Tenant-Id", TENANT)
                        .header("X-User-Id", "writer"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode material = objectMapper.readTree(uploaded).path("data");
        assertThat(material.path("selectedFragment").asText()).contains("扫描件识别出的文字");

        JsonNode request = objectMapper.readTree(RECEIVED.get());
        assertThat(request.path("filename").asText()).isEqualTo("scan.pdf");
        assertThat(Base64.getDecoder().decode(request.path("content").asText())).isEqualTo(scanned);
    }

    /** 只有空白页面、没有文字层的 PDF，模拟扫描件。 */
    private static byte[] imageOnlyPdf() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static HttpServer startOcrServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/ocr", exchange -> {
                RECEIVED.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"text\":\"扫描件识别出的文字：全年完成改造30项。\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
