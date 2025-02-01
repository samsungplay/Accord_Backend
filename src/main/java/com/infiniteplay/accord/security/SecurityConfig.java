package com.infiniteplay.accord.security;


import com.infiniteplay.accord.security.authentication.*;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {


    private final JWTAuthenticationProvider jwtAuthenticationProvider;
    private final JwtBasicAuthenticationEntryPoint  jwtBasicAuthenticationEntryPoint;
    private final JWTSuccessfulAuthenticationFilter jwtSuccessfulAuthenticationFilter;
    private final JWTFilter jwtFilter;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> githubUserService;
    private final GithubAuthenticationSuccessHandler githubAuthenticationSuccessHandler;
    private final GithubAuthenticationFailureHandler githubAuthenticationFailureHandler;

    @Value("${client.url}")
    private String clientUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        http.authenticationProvider(jwtAuthenticationProvider).
                exceptionHandling(httpSecurityExceptionHandlingConfigurer -> {
                    httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(jwtBasicAuthenticationEntryPoint);
                }).
                sessionManagement((sessionManagement) -> {
                    sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                }).
                httpBasic((httpSecurityHttpBasicConfigurer -> {
                    httpSecurityHttpBasicConfigurer.authenticationEntryPoint(jwtBasicAuthenticationEntryPoint);
                })).
                oauth2Login((oauth2LoginConfigurer -> {
                    oauth2LoginConfigurer
                            .userInfoEndpoint(userInfoEndpoint -> {
                                userInfoEndpoint.userService(githubUserService);
                            })


                            .successHandler(githubAuthenticationSuccessHandler)
                            .failureHandler(githubAuthenticationFailureHandler);
                })).
                csrf((csrf) -> {
//                    csrf.ignoringRequestMatchers("/favicon.ico","/login","/authentication/registerGithub","/authentication/register", "/authentication/authenticate","/error","/authentication/github","/login/oauth2/code/github")
//                            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
//                            .csrfTokenRequestHandler(new SinglePageCSRFTokenRequestHandler());
                    //no need for csrf protection when using stateless jwt authentication w/ header!
                    csrf.disable();

                }).authorizeHttpRequests((authorizeHttpRequests) -> {
                    authorizeHttpRequests.
                            requestMatchers("/authentication/authenticate","/users/**","/chatrooms/**","/chat/**",
                                    "/fetchmetadata/**","/call/**", "/pushNotification/**").authenticated()
                            .anyRequest().permitAll();
//                    authorizeHttpRequests.requestMatchers("/favicon.ico","/login","/authentication/register","/authentication/csrfTest","/error","/authentication/github","/login/oauth2/code/github").permitAll()
//                            .anyRequest().authenticated();
                }).cors((cors) -> cors.configurationSource(corsConfigurationSource()))
//                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(jwtSuccessfulAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, BasicAuthenticationFilter.class);



        return http.build();
    }


    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(Arrays.asList("http://localhost:3000",clientUrl));
        corsConfiguration.setAllowedMethods(Arrays.asList("POST","GET","DELETE","UPDATE","PUT", "HEAD"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",corsConfiguration);
        return source;
    }

}
