package com.otd.otd_msa_back_life.configuration.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    private final UserHeaderAuthenticationFilter userHeaderAuthenticationFilter;

    @Bean //스프링이 메소드 호출을 하고 리턴한 객체의 주소값을 관리한다. (빈등록)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(h -> h.disable())
                .csrf(c -> c.disable())
                .addFilterBefore(userHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스는 공개
                        .requestMatchers(HttpMethod.GET, "/otd-api/static/**", "/static/**").permitAll()
                        // API 보호 구간
                        .requestMatchers("/api/OTD/exercise/**").authenticated()
                        .requestMatchers("/api/OTD/meal/**").authenticated()
                        .requestMatchers("/api/OTD/community/**", "/api/OTD/community/posts/**").authenticated()
                        .anyRequest().permitAll())
        .build();
    }
}