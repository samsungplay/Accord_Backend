package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteplay.accord.repositories.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class JWTFilter extends OncePerRequestFilter {

    private final JWTHandler jwtHandler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserRepository userRepository;
    @Value("${process.env}")
    String processEnv;

    public JWTFilter(JWTHandler jwtHandler, UserRepository userRepository) {
        this.jwtHandler = jwtHandler;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


        Cookie[] cookies = request.getCookies();

        String accessToken = null;
        String refreshToken = null;



        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("accord_access_token")) {
                    accessToken = cookie.getValue();
                } else if (cookie.getName().equals("accord_refresh_token")) {
                    refreshToken = cookie.getValue();
                }
            }
        }
        //validate jwt authorization header
        if (accessToken != null) {
            try {
                Authentication authentication = jwtHandler.readAccessToken(accessToken);

                //check if user actually exists
                String username = authentication.getName().split("@")[0];
                int id = Integer.parseInt(authentication.getName().split("@")[1]);

                if (!userRepository.existsByUsernameWithId(id,username)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    Map<String, String> errors = new HashMap<>();
                    errors.put("error", "invalid user");
                    response.getOutputStream().println(mapper.writeValueAsString(errors));
                } else {
                    //jwt valid!
                    SecurityContext securityContext = SecurityContextHolder.getContext();
                    securityContext.setAuthentication(authentication);
                    logger.info("Jwt verified");
                }


                filterChain.doFilter(request, response);
            } catch (ExpiredJwtException e) {
                //jwt expired (i.e. access token expired)
                logger.debug("Jwt expired");
                //try to refresh using refresh token
                if (refreshToken != null) {
                    try {
                        Authentication authentication = jwtHandler.readRefreshToken(refreshToken);
                        String username = authentication.getName().split("@")[0];
                        int id = Integer.parseInt(authentication.getName().split("@")[1]);
                        if (!userRepository.existsByUsernameWithId(id,username)) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            Map<String, String> errors = new HashMap<>();
                            errors.put("error", "invalid user");
                            response.getOutputStream().println(mapper.writeValueAsString(errors));
                        } else {
                            String newAccessToken = jwtHandler.createToken(authentication.getName(), authentication.getAuthorities(), true);
                            //refresh access token, (issue a new access token)
                            ResponseCookie cookie = ResponseCookie.from("accord_access_token", newAccessToken)
                                    .path("/")
                                    .sameSite(processEnv.equals("prod") ? "Lax" : "None")
                                    .httpOnly(true)
                                    .secure(true)
                                    .build();
                            response.addHeader("Set-Cookie", cookie.toString());
                            logger.debug("jwt refreshed");
                        }
                    } catch (Exception ex) {
                        //refresh token also invalid
                        filterChain.doFilter(request, response);
                    }
                } else {
                    filterChain.doFilter(request, response);
                }

            } catch (JwtException e) {
                //jwt outright invalid; ignore
                logger.debug("Jwt outright invalid");
                filterChain.doFilter(request, response);
            }

        } else {
            filterChain.doFilter(request, response);
        }


    }
}
