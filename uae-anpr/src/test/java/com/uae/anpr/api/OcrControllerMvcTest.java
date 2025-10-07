package com.uae.anpr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.config.AnprProperties.OcrProperties;
import com.uae.anpr.config.AnprProperties.ResourceSet;
import com.uae.anpr.service.pipeline.RecognitionPipeline;
import com.uae.anpr.service.parser.UaePlateParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcrController.class)
@Import(OcrControllerMvcTest.TestConfig.class)
class OcrControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecognitionPipeline recognitionPipeline;

    @MockBean
    private UaePlateParser uaePlateParser;

    @Test
    void recognizeReturnsBadRequestWhenImageIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/anpr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recognizeReturnsBadRequestWhenImageIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/anpr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageBase64\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AnprProperties anprProperties() {
            return new AnprProperties(
                    new ResourceSet(null, null, null, null),
                    new OcrProperties("eng", 0.85, false, null, null));
        }

    }
}
