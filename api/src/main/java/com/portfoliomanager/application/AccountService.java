package com.portfoliomanager.application;

import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.TransactionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<AccountView> list() {
        return accounts.findAllByOrderByNameAsc().stream().map(AccountService::view).toList();
    }

    @Transactional
    public AccountView create(String name, AccountType type) {
        String trimmed = name.trim();
        if (accounts.existsByName(trimmed)) {
            throw nameTaken(trimmed);
        }
        return view(accounts.saveAndFlush(new AccountEntity(trimmed, type)));
    }

    @Transactional
    public AccountView update(UUID id, String name, AccountType type) {
        AccountEntity account = find(id);
        String trimmed = name.trim();
        if (accounts.existsByNameAndIdNot(trimmed, id)) {
            throw nameTaken(trimmed);
        }
        account.setName(trimmed);
        account.setType(type);
        return view(account);
    }

    @Transactional
    public void delete(UUID id) {
        AccountEntity account = find(id);
        if (transactions.existsByAccountId(id)) {
            throw new ConflictException(
                    "Account %s has transactions and cannot be deleted"
                            .formatted(account.getName()));
        }
        try {
            accounts.delete(account);
            accounts.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Account %s has history and cannot be deleted".formatted(account.getName()));
        }
    }

    private AccountEntity find(UUID id) {
        return accounts.findById(id)
                .orElseThrow(
                        () -> new NotFoundException("Account %s does not exist".formatted(id)));
    }

    private static ConflictException nameTaken(String name) {
        return new ConflictException("An account named %s already exists".formatted(name));
    }

    private static AccountView view(AccountEntity entity) {
        return new AccountView(
                entity.getId(), entity.getName(), entity.getType(), entity.getCreatedAt());
    }
}
