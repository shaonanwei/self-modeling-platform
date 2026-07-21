package com.selfmodeling.config;

import cn.dev33.satoken.spring.SaTokenContextRegister;
import com.selfmodeling.controller.MetadataController;
import com.selfmodeling.controller.SqlController;
import com.selfmodeling.exception.GlobalExceptionHandler;
import com.selfmodeling.service.MetadataService;
import com.selfmodeling.service.SqlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SqlController.class, MetadataController.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, SaTokenContextRegister.class})
@ContextConfiguration(classes = {
        SqlController.class,
        MetadataController.class,
        SaTokenConfig.class,
        GlobalExceptionHandler.class,
        SaTokenContextRegister.class
})
class ApiAuthenticationBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlService sqlService;

    @MockitoBean
    private MetadataService metadataService;

    @Test
    void anonymousSqlExecutionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/sql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\",\"dataSourceId\":\"master\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void anonymousMetadataAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/datasources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
