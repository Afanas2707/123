package org.nobilis.nobichat.filters;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import java.util.Set;

@Component
@WebFilter(urlPatterns = "/*")
public class RequestLoggingFilter extends AbstractRequestLoggingFilter {

    private Set<String> excludedUrls = Set.of("/actuator/health/readiness", "/actuator/health/liveness");

    public RequestLoggingFilter() {
        setIncludeClientInfo(true);
        setIncludeHeaders(false);
        setIncludePayload(false);
        setIncludeQueryString(true);
        setBeforeMessagePrefix("Request started => ");
    }

    @Override
    protected boolean shouldLog(HttpServletRequest request) {
        if (excludedUrls.contains(request.getRequestURI())) {
            return false;
        }
        return logger.isDebugEnabled();
    }

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        logger.info(message);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {}
}
