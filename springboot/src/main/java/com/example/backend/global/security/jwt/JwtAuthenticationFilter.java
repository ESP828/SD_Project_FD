package com.example.backend.global.security.jwt;

import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AccountRepository accountRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, AccountRepository accountRepository) {
        this.jwtProvider = jwtProvider;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtProvider.validateToken(token)) {
            Long accountId = jwtProvider.getAccountId(token);
            if (!accountRepository.existsByAccountIdAndStatus(accountId, AccountStatus.ACTIVE)) {
                filterChain.doFilter(request, response);
                return;
            }
            String loginId = jwtProvider.getLoginId(token);
            List<String> authorities = jwtProvider.getAuthorities(token);
            AuthenticatedAccount principal = new AuthenticatedAccount(accountId, loginId, authorities);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities.stream().map(SimpleGrantedAuthority::new).toList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
