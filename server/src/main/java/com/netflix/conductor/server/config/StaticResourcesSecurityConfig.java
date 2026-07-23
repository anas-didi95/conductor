/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

public class StaticResourcesSecurityConfig {

    @Configuration
    @EnableWebSecurity
    @EnableConfigurationProperties(StaticResourcesProtectionProperties.class)
    @ConditionalOnExpression(
            "'${conductor.enable.ui.serving:true}'.equals('true') && "
                    + "'${conductor.ui.security.static-resources-protection.enabled:false}'.equals('true')")
    public static class ProtectedSecurityConfig {

        private final StaticResourcesProtectionProperties properties;

        public ProtectedSecurityConfig(StaticResourcesProtectionProperties properties) {
            this.properties = properties;
        }

        @Bean
        @Order(1)
        public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(
                            authz ->
                                    authz.requestMatchers(
                                                    "/api/**",
                                                    "/health",
                                                    "/health/**",
                                                    "/actuator",
                                                    "/actuator/**",
                                                    "/api-docs",
                                                    "/api-docs/**",
                                                    "/v3/api-docs",
                                                    "/v3/api-docs/**",
                                                    "/swagger-ui",
                                                    "/swagger-ui/**",
                                                    "/a2a",
                                                    "/a2a/**",
                                                    "/error",
                                                    "/login",
                                                    "/login/**",
                                                    "/login.html",
                                                    "/conductorLogo.svg",
                                                    "/conductorLogo.png",
                                                    "/conductorLogo-dark.svg",
                                                    "/conductorLogoSmall.svg",
                                                    "/conductorLogoSmall.png",
                                                    "/orkes-logo-purple-2x.png",
                                                    "/orkes-logo-purple-inverted-2x.png",
                                                    "/logo.png",
                                                    "/robots.txt",
                                                    "/favicon.ico")
                                            .permitAll()
                                            .anyRequest()
                                            .authenticated())
                    .formLogin(
                            form ->
                                    form.loginPage("/login.html")
                                            .loginProcessingUrl("/login")
                                            .defaultSuccessUrl("/", true))
                    .logout(
                            logout ->
                                    logout.logoutUrl("/logout")
                                            .invalidateHttpSession(true)
                                            .clearAuthentication(true)
                                            .deleteCookies("JSESSIONID")
                                            .logoutSuccessUrl("/login.html?logout"))
                    .sessionManagement(
                            session -> {
                                session.sessionFixation().migrateSession();
                                session.sessionCreationPolicy(
                                        org.springframework.security.config.http
                                                .SessionCreationPolicy.IF_REQUIRED);
                                session.maximumSessions(1).maxSessionsPreventsLogin(false);
                                session.invalidSessionUrl("/login.html?expired");
                            })
                    .headers(
                            headers -> {
                                headers.contentTypeOptions();
                                headers.frameOptions(frameOptions -> frameOptions.deny());
                                headers.cacheControl();
                            });
            if (properties.isCsrfEnabled()) {
                http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                        org.springframework.security.web.csrf
                                                .CookieCsrfTokenRepository.withHttpOnlyFalse()));
            } else {
                http.csrf(AbstractHttpConfigurer::disable);
            }
            return http.build();
        }

        @Bean
        public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
            for (StaticResourcesProtectionProperties.UserConfig user : properties.getUsers()) {
                String encodedPassword =
                        isBcryptHash(user.getPassword())
                                ? user.getPassword()
                                : passwordEncoder.encode(user.getPassword());
                UserDetails details =
                        User.builder()
                                .username(user.getUsername())
                                .password(encodedPassword)
                                .roles(user.getRoles().split(","))
                                .build();
                manager.createUser(details);
            }
            return manager;
        }

        private boolean isBcryptHash(String password) {
            return password != null
                    && (password.startsWith("$2a$")
                            || password.startsWith("$2b$")
                            || password.startsWith("$2y$"));
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Configuration
    @EnableWebSecurity
    @ConditionalOnExpression(
            "'${conductor.enable.ui.serving:true}'.equals('true') && "
                    + "!'${conductor.ui.security.static-resources-protection.enabled:false}'.equals('true')")
    public static class PermitAllSecurityConfig {

        @Bean
        @Order(99)
        public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .logout(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    @Configuration
    @EnableWebSecurity
    @ConditionalOnProperty(name = "conductor.enable.ui.serving", havingValue = "false")
    public static class UiServingDisabledConfig {

        @Bean
        @Order(98)
        public SecurityFilterChain uiServingDisabledFilterChain(HttpSecurity http)
                throws Exception {
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .logout(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }
}
