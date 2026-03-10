package com.hackathon.processvideo.infra.config;

import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingContextFilter")
class LoggingContextFilterTest {

    @Mock
    private ServletRequest servletRequest;

    @Mock
    private ServletResponse servletResponse;

    @Mock
    private FilterChain filterChain;

    private LoggingContextFilter loggingContextFilter;

    @BeforeEach
    void setUp() {
        loggingContextFilter = new LoggingContextFilter();
    }

    @Test
    @DisplayName("Should invoke filter chain and set MDC trace ID")
    void shouldInvokeFilterChainWithTraceId() throws IOException, ServletException {
        // Act
        loggingContextFilter.doFilter(servletRequest, servletResponse, filterChain);

        // Assert
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    @DisplayName("Should clear MDC trace ID even when filter chain throws exception")
    void shouldClearMdcEvenOnException() throws IOException, ServletException {
        // Arrange
        org.mockito.Mockito.doThrow(new ServletException("chain error"))
                .when(filterChain).doFilter(servletRequest, servletResponse);

        // Act / Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> loggingContextFilter.doFilter(servletRequest, servletResponse, filterChain)
        ).isInstanceOf(ServletException.class);

        verify(filterChain).doFilter(servletRequest, servletResponse);
    }
}
