package com.infiniteplay.accord.security.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    public JWTFilter(JWTHandler jwtHandler) {
        this.jwtHandler = jwtHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


        //validate jwt authorization header
        if(request.getHeader("Authorization") != null) {
            String header = request.getHeader("Authorization");
            if(header.startsWith("Bearer ")) {
                String token = header.substring(7).strip();
                try {
                    Authentication authentication = jwtHandler.readAccessToken(token);
                    //jwt valid!
                    SecurityContext securityContext = SecurityContextHolder.getContext();
                    securityContext.setAuthentication(authentication);

                    filterChain.doFilter(request, response);
                } catch (ExpiredJwtException e) {
                    //jwt expired (i.e. access token expired)
                    logger.debug("Jwt expired");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    Map<String,String> errors = new HashMap<>();
                    errors.put("error", "invalid token");
                    response.getOutputStream().println(mapper.writeValueAsString(errors));
                }
                catch(JwtException e) {
                    //jwt outright invalid; ignore
                    logger.debug("Jwt outright invalid");
                    filterChain.doFilter(request, response);
                }
            }
            else {
                logger.debug("filterChain invoked");
                filterChain.doFilter(request, response);
            }
        }

        else if(request.getHeader("Refresh-Token") != null) {
            String header = request.getHeader("Refresh-Token");
            try {
                Authentication authentication = jwtHandler.readRefreshToken(header);
                //is refresh token
                logger.debug("Jwt refreshed");
                response.setStatus(HttpServletResponse.SC_CREATED);
//                response.setHeader("Access-Token", jwtHandler.createToken(authentication.getName(), authentication.getAuthorities(),false));
                Map<String, String> accessTokenMap = new HashMap<>();
                accessTokenMap.put("access_token",jwtHandler.createToken(authentication.getName(), authentication.getAuthorities(),false) );
                response.getOutputStream().println(mapper.writeValueAsString(accessTokenMap));
            }
            catch(JwtException e) {
                logger.debug("Jwt refresh invaild");
                filterChain.doFilter(request, response);
            }
        }

        else {
            logger.info("passthrough");
            filterChain.doFilter(request, response);
        }



    }
}
