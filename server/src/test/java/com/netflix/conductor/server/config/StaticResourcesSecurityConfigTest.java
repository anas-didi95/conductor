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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.netflix.conductor.Conductor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Conductor.class)
@AutoConfigureMockMvc
public class StaticResourcesSecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @Nested
    @TestPropertySource(
            properties = "conductor.ui.security.static-resources-protection.enabled=false")
    class FeatureDisabledTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void healthEndpointIsAccessible() throws Exception {
            mockMvc.perform(get("/health")).andExpect(status().isOk());
        }

        @Test
        void staticRootIsAccessible() throws Exception {
            mockMvc.perform(get("/")).andExpect(status().isOk());
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=alice-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER,ADMIN",
                "conductor.ui.security.static-resources-protection.users[1].username=bob",
                "conductor.ui.security.static-resources-protection.users[1].password=bob-secret",
                "conductor.ui.security.static-resources-protection.users[1].roles=USER"
            })
    class FeatureEnabledMultiUserTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void healthEndpointIsAccessible() throws Exception {
            mockMvc.perform(get("/health")).andExpect(status().isOk());
        }

        @Test
        void loginPageIsAccessible() throws Exception {
            mockMvc.perform(get("/login.html")).andExpect(status().isOk());
        }

        @Test
        void staticRootRedirectsToLogin() throws Exception {
            mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
        }

        @Test
        void aliceCanLogin() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "alice-secret")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        void bobCanLogin() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "bob")
                                    .param("password", "bob-secret")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        void alicePasswordDoesNotAuthenticateBob() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "bob")
                                    .param("password", "alice-secret")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login.html?error"));
        }

        @Test
        void badCredentialsRedirectToLoginWithError() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "wrong")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login.html?error"));
        }

        @Test
        void logoutInvalidatesSession() throws Exception {
            mockMvc.perform(
                            post("/logout")
                                    .with(
                                            org.springframework.security.test.web.servlet.request
                                                    .SecurityMockMvcRequestPostProcessors.user(
                                                            "alice")
                                                    .password("alice-secret")
                                                    .roles("USER"))
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login.html?logout"))
                    .andExpect(
                            header().string(
                                            "Set-Cookie",
                                            org.hamcrest.Matchers.containsString("JSESSIONID=;")));
        }

        @Test
        void securityHeadersPresentOnAuthenticatedResponse() throws Exception {
            mockMvc.perform(
                            get("/").with(
                                            org.springframework.security.test.web.servlet.request
                                                    .SecurityMockMvcRequestPostProcessors.user(
                                                            "alice")
                                                    .password("alice-secret")
                                                    .roles("USER")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(
                            header().string(
                                            "Cache-Control",
                                            "no-cache, no-store, max-age=0, must-revalidate"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=$2a$10$pIVv257DcEAVj6yruVSskOWqqOPcQDqYzU/frWGI9NwJHm48DVCu.",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER"
            })
    class HashedPasswordTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void hashedPasswordLoginSucceeds() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "secret")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.csrf-enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=alice-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER"
            })
    class CsrfEnabledTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void loginWithoutCsrfTokenIsNotSuccessful() throws Exception {
            // Anonymous user + missing CSRF is rejected. The exact redirect URL
            // depends on Spring Security's exception handling, but the login must
            // not succeed (i.e., must not redirect to the default success URL "/").
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "alice-secret"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(
                            result ->
                                    org.junit.jupiter.api.Assertions.assertNotEquals(
                                            "/",
                                            result.getResponse().getRedirectedUrl(),
                                            "Login should not succeed without CSRF token"));
        }

        @Test
        void loginWithCsrfTokenSucceeds() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "alice-secret")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.csrf-enabled=false",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=alice-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER"
            })
    class CsrfDisabledTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void loginWithoutCsrfTokenSucceeds() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "alice")
                                    .param("password", "alice-secret"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.enable.ui.serving=false",
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=alice-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER"
            })
    class UiServingDisabledTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void staticRootIsAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/")).andExpect(status().isOk());
        }

        @Test
        void healthEndpointIsAccessible() throws Exception {
            mockMvc.perform(get("/health")).andExpect(status().isOk());
        }
    }

    @Nested
    @TestPropertySource(
            properties = "conductor.ui.security.static-resources-protection.enabled=true")
    class EmptyUsersTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void noCredentialsAreAccepted() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "admin")
                                    .param("password", "admin")
                                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login.html?error"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=alice",
                "conductor.ui.security.static-resources-protection.users[0].password=alice-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=USER"
            })
    class LoginPageAssetsArePublicTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void logoSvgIsAccessible() throws Exception {
            mockMvc.perform(get("/conductorLogo.svg")).andExpect(status().isOk());
        }

        @Test
        void faviconIsAccessible() throws Exception {
            mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk());
        }

        @Test
        void robotsTxtIsAccessible() throws Exception {
            mockMvc.perform(get("/robots.txt")).andExpect(status().isOk());
        }
    }
}
