package at.htlle.freq.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures optional HTTP Basic authentication for backend interfaces.
 *
 * <p>Static UI assets stay publicly reachable, while backend endpoints can be
 * protected by a preset username/password pair.</p>
 */
@Configuration
public class BackendSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(BackendSecurityConfig.class);

    @Value("${lifex.security.backend.enabled:true}")
    private boolean backendSecurityEnabled;

    @Value("${lifex.security.backend.username:lifex}")
    private String username;

    @Value("${lifex.security.backend.password:12345}")
    private String password;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        if (!backendSecurityEnabled) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        http.httpBasic(Customizer.withDefaults());
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/",
                        "/index.html",
                        "/create.html",
                        "/details.html",
                        "/reports.html",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/error"
                ).permitAll()
                .requestMatchers(
                        "/api/**",
                        "/h2-console/**",
                        "/search/**",
                        "/table/**",
                        "/row/**",
                        "/accounts/**",
                        "/projects/**",
                        "/sites/**",
                        "/servers/**",
                        "/clients/**",
                        "/radios/**",
                        "/audio/**",
                        "/phones/**",
                        "/deployment-variants/**",
                        "/servicecontracts/**",
                        "/addresses/**"
                ).authenticated()
                .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        if (backendSecurityEnabled) {
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException("Backend security is enabled, but username/password are blank.");
            }
            if ("lifex".equals(username) && "12345".equals(password)) {
                log.warn("Backend security uses default credentials (lifex/12345). Configure lifex.security.backend.* in production.");
            }
        }
        UserDetails backendUser = User.withUsername(username)
                .password("{noop}" + password)
                .roles("BACKEND")
                .build();
        return new InMemoryUserDetailsManager(backendUser);
    }
}
