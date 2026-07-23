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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rbac")
public class RbacUserRoleController {

    private final RbacConfigurationProperties rbacProperties;

    public RbacUserRoleController(RbacConfigurationProperties rbacProperties) {
        this.rbacProperties = rbacProperties;
    }

    @GetMapping("/current-user-role")
    public Map<String, String> getCurrentUserRole() {
        if (!rbacProperties.isEnabled()) {
            return Map.of("role", "ADMIN");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) {
            return Map.of("role", "ADMIN");
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.startsWith("ROLE_")) {
                role = role.substring(5);
            }
            try {
                UserRole.valueOf(role);
                return Map.of("role", role);
            } catch (IllegalArgumentException e) {
                // not a valid UserRole, continue to next authority
            }
        }

        return Map.of("role", "VIEWER");
    }
}
