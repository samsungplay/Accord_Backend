package com.infiniteplay.accord.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

public class SinglePageCSRFTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    private final CsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferredCsrfToken) {
        //When making the persisted csrf token available to rest of the application, use BREACH protection and encode CSRF token with
        //extra randomness.
        this.delegate.handle(request, response, deferredCsrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {

        if(request.getHeader(csrfToken.getHeaderName()) != null && request.getHeader(csrfToken.getHeaderName()).length() > 0) {
            //when single page application submits csrf token through header, it only contains the plain value so this token handler must delegate to the
            //vanilla requestattributehandler to resolve the token.
            return super.resolveCsrfTokenValue(request,csrfToken);
        }

        //otherwise, let xors token handler do it.
        return super.resolveCsrfTokenValue(request, csrfToken);
    }
}
