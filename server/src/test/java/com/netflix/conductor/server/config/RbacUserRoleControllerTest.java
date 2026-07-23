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

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.Conductor;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Conductor.class)
public class RbacUserRoleControllerTest {

    @Nested
    class RbacDisabledTest {

        @Autowired private TestRestTemplate restTemplate;

        @Test
        void returnsAdminWhenRbacDisabled() {
            ResponseEntity<Map> response =
                    restTemplate.getForEntity("/api/rbac/current-user-role", Map.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("ADMIN", response.getBody().get("role"));
        }
    }

    @Nested
    @TestPropertySource(properties = "conductor.rbac.enabled=true")
    class RbacEnabledUnauthenticatedTest {

        @Autowired private TestRestTemplate restTemplate;

        @Test
        void returnsAdminWhenUnauthenticated() {
            ResponseEntity<Map> response =
                    restTemplate.getForEntity("/api/rbac/current-user-role", Map.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("ADMIN", response.getBody().get("role"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.rbac.enabled=true",
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=viewer",
                "conductor.ui.security.static-resources-protection.users[0].password=viewer-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=VIEWER"
            })
    class RbacEnabledViewerTest {

        @Autowired private TestRestTemplate restTemplate;

        @Test
        void returnsViewerForAuthenticatedViewer() {
            TestRestTemplate authRestTemplate =
                    restTemplate.withBasicAuth("viewer", "viewer-secret");
            ResponseEntity<Map> response =
                    authRestTemplate.getForEntity("/api/rbac/current-user-role", Map.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("VIEWER", response.getBody().get("role"));
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "conductor.rbac.enabled=true",
                "conductor.ui.security.static-resources-protection.enabled=true",
                "conductor.ui.security.static-resources-protection.users[0].username=admin",
                "conductor.ui.security.static-resources-protection.users[0].password=admin-secret",
                "conductor.ui.security.static-resources-protection.users[0].roles=ADMIN"
            })
    class RbacEnabledAdminTest {

        @Autowired private TestRestTemplate restTemplate;

        @Test
        void returnsAdminForAuthenticatedAdmin() {
            TestRestTemplate authRestTemplate = restTemplate.withBasicAuth("admin", "admin-secret");
            ResponseEntity<Map> response =
                    authRestTemplate.getForEntity("/api/rbac/current-user-role", Map.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("ADMIN", response.getBody().get("role"));
        }
    }
}
