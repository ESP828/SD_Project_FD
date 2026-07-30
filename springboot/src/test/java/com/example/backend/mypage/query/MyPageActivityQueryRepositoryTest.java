package com.example.backend.mypage.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MyPageActivityQueryRepositoryTest {

    @Autowired
    private MyPageActivityQueryRepository repository;

    @Test
    void returnsEmptyDetailListsWhenTheAccountHasNoActivity() {
        long accountId = Long.MAX_VALUE;

        assertAll(
                () -> assertTrue(repository.findFavorites(accountId).isEmpty()),
                () -> assertTrue(repository.findReviews(accountId).isEmpty()),
                () -> assertTrue(repository.findPosts(accountId).isEmpty()),
                () -> assertTrue(repository.findComments(accountId).isEmpty()),
                () -> assertTrue(repository.findUnreadNotifications(accountId).isEmpty())
        );
    }
}
