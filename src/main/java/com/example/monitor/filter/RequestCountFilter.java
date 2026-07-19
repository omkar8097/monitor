package com.example.monitor.filter;

import jakarta.servlet.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RequestCountFilter implements Filter {
    private static final AtomicLong requestCount = new AtomicLong(0);

    public static long getRequestCount() {
        return requestCount.get();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        requestCount.incrementAndGet();
        chain.doFilter(request, response);
    }
}
