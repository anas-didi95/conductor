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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
            properties = "conductor.ui.security.static-resources-protection.enabled=true")
    class FeatureEnabledTest {

        @Autowired private MockMvc mockMvc;

        @Test
        void healthEndpointIsAccessible() throws Exception {
            mockMvc.perform(get("/health")).andExpect(status().isOk());
        }

        @Test
        void staticRootRedirectsToLogin() throws Exception {
            mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
        }

        @Test
        void loginPageIsAccessible() throws Exception {
            mockMvc.perform(get("/login.html")).andExpect(status().isOk());
        }
    }
}
