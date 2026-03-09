package com.financetracker.service.impl;

import com.financetracker.dto.request.AccountRequest;
import com.financetracker.entity.Account;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(value = "accounts", key = "#userEmail")
    public Account createAccount(String userEmail, AccountRequest request) {
        User user = getActiveUser(userEmail);

        Account account = Account.builder()
                .user(user)
                .name(request.getName())
                .accountType(request.getAccountType())
                .balance(request.getInitialBalance())
                .currency(request.getCurrency())
                .color(request.getColor())
                .icon(request.getIcon())
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: uuid={}, user={}", saved.getUuid(), userEmail);
        return saved;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "accounts", key = "#userEmail")
    public List<Account> getAccounts(String userEmail) {
        User user = getActiveUser(userEmail);
        return accountRepository.findByUserIdAndIsActiveTrue(user.getId());
    }

    @Transactional(readOnly = true)
    public Account getAccount(String userEmail, String accountUuid) {
        return accountRepository.findByUuidAndUserEmail(accountUuid, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountUuid));
    }

    @Transactional
    @CacheEvict(value = "accounts", key = "#userEmail")
    public Account updateAccount(String userEmail, String accountUuid, AccountRequest request) {
        Account account = getAccount(userEmail, accountUuid);
        account.setName(request.getName());
        account.setColor(request.getColor());
        account.setIcon(request.getIcon());
        Account saved = accountRepository.save(account);
        log.info("Account updated: uuid={}", accountUuid);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "accounts", key = "#userEmail")
    public void deleteAccount(String userEmail, String accountUuid) {
        Account account = getAccount(userEmail, accountUuid);
        account.setActive(false); // Soft delete
        accountRepository.save(account);
        log.info("Account soft-deleted: uuid={}", accountUuid);
    }

    private User getActiveUser(String email) {
        return userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}