package com.claims.platform.web;

import com.claims.platform.common.error.ErrorBody;
import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.common.security.AuthenticatedUser;
import com.claims.platform.common.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ClaimsSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtService jwtService, ClaimsSecurityProperties properties, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain filterChain)
            throws jakarta.servlet.ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (matches(path, properties.getInternalPaths())) {
            String internal = request.getHeader("X-Internal-Token");
            if (internal != null && internal.equals(properties.getInternalToken())) {
                authenticate(new AuthenticatedUser("service", "service@claims.local", "Service", "SERVICE",
                        null, List.of(), null));
                filterChain.doFilter(request, response);
                return;
            }
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                authenticate(jwtService.parse(header.substring(7).trim()));
            } catch (RuntimeException ex) {
                write(response, request, 401, ErrorCodes.UNAUTHORIZED, "Missing or invalid token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedUser user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean matches(String path, List<String> patterns) {
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object correlation = request.getAttribute(CorrelationIdFilter.ATTR);
        String correlationId = correlation == null ? null : correlation.toString();
        objectMapper.writeValue(response.getOutputStream(), new ErrorBody(code, message, List.of(), correlationId));
    }
}
