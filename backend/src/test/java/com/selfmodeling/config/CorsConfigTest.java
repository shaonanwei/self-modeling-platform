package com.selfmodeling.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class CorsConfigTest {

    @Test
    void allowsConfiguredDevelopmentOrigin() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(
                new CorsProperties(List.of("http://127.0.0.1:5173")));
        MockHttpServletRequest request = preflight("http://127.0.0.1:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals("http://127.0.0.1:5173",
                response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter(
                new CorsProperties(List.of("http://127.0.0.1:5173")));
        MockHttpServletRequest request = preflight("https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("OPTIONS", "/api/v1/auth/captcha");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        return request;
    }
}
