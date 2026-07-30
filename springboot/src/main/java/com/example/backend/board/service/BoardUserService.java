package com.example.backend.board.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.board.exception.BoardException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardUserService {

    private final AccountRepository accountRepository;

    public BoardUserService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Account findOptional(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Account require(Long accountId) {
        if (accountId == null) {
            throw new BoardException(
                    HttpStatus.UNAUTHORIZED,
                    "BOARD_AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BoardException(
                        HttpStatus.UNAUTHORIZED,
                        "BOARD_ACCOUNT_NOT_FOUND",
                        "인증된 계정 정보를 찾을 수 없습니다."
                ));
        if (!account.isActive()) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_ACCOUNT_UNAVAILABLE",
                    "현재 게시판을 사용할 수 없는 계정입니다."
            );
        }
        return account;
    }
}
