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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(
            name = "conductor.ui.security.static-resources-protection.enabled",
            havingValue = "true")
    public static class ProtectedSecurityConfig {

        @Value("${conductor.ui.security.static-resources-protection.username:admin}")
        private String username;

        @Value("${conductor.ui.security.static-resources-protection.password:admin}")
        private String password;

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
                                                    "/favicon.ico")
                                            .permitAll()
                                            .anyRequest()
                                            .authenticated())
                    .formLogin(
                            form ->
                                    form.loginPage("/login.html")
                                            .loginProcessingUrl("/login")
                                            .defaultSuccessUrl("/", true))
                    .logout(logout -> logout.logoutSuccessUrl("/login.html?logout"))
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }

        @Bean
        public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            UserDetails user =
                    User.builder()
                            .username(username)
                            .password(passwordEncoder.encode(password))
                            .roles("USER")
                            .build();
            return new InMemoryUserDetailsManager(user);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Configuration
    @EnableWebSecurity
    @ConditionalOnProperty(
            name = "conductor.ui.security.static-resources-protection.enabled",
            havingValue = "false",
            matchIfMissing = true)
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
}
