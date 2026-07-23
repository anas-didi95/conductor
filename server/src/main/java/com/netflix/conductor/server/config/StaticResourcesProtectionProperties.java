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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.ui.security.static-resources-protection")
public class StaticResourcesProtectionProperties {

    private boolean enabled;

    /**
     * Whether CSRF protection is enabled for the login form.
     *
     * <p><strong>Default is {@code false}</strong> because the bundled login page is a static HTML
     * file and cannot include a CSRF token without JavaScript. Set to {@code true} if you serve a
     * custom login page that reads the {@code XSRF-TOKEN} cookie (set by {@code
     * CookieCsrfTokenRepository}) and sends it back as the {@code X-XSRF-TOKEN} header.
     */
    private boolean csrfEnabled = false;

    private List<UserConfig> users = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCsrfEnabled() {
        return csrfEnabled;
    }

    public void setCsrfEnabled(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }

    public List<UserConfig> getUsers() {
        return users;
    }

    public void setUsers(List<UserConfig> users) {
        this.users = users;
    }

    public static class UserConfig {
        private String username;
        private String password;
        private String roles = "USER";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRoles() {
            return roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }
    }
}
