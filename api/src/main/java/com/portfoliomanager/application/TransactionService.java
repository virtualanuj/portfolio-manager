package com.portfoliomanager.application;

import com.portfoliomanager.domain.FifoEngine;
import com.portfoliomanager.domain.ReplayResult;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change to a position is replayed through {@link FifoEngine} inside the same database
 * transaction; a violation throws {@link OversellException}, which rolls the change back.
 */
@Service
public class TransactionService {

    private static final int QUANTITY_SCALE = 8;
    private static final int MAX_INTEGER_DIGITS = 16;
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final PositionLoader positionLoader;

    public TransactionService(
            TransactionRepository transactions,
            AccountRepository accounts,
            InstrumentRepository instruments,
            PositionLoader positionLoader) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.instruments = instruments;
        this.positionLoader = positionLoader;
    }

    @Transactional(readOnly = true)
    public ReplayResult replayFor(UUID accountId, UUID instrumentId) {
        return FifoEngine.replay(positionLoader.load(accountId, instrumentId));
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> list(TransactionFilter filter, int page, int size) {
        PageRequest pageable =
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Order.desc("tradeDate"), Sort.Order.desc("seq")));
        Page<TransactionEntity> result = transactions.findAll(matching(filter), pageable);

        Map<UUID, AccountEntity> accountById =
                accounts
                        .findAllById(result.stream().map(TransactionEntity::getAccountId).toList())
                        .stream()
                        .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<UUID, InstrumentEntity> instrumentById =
                instruments
                        .findAllById(
                                result.stream().map(TransactionEntity::getInstrumentId).toList())
                        .stream()
                        .collect(Collectors.toMap(InstrumentEntity::getId, Function.identity()));
        return result.map(
                t ->
                        view(
                                t,
                                accountById.get(t.getAccountId()),
                                instrumentById.get(t.getInstrumentId())));
    }

    @Transactional
    public TransactionView create(TransactionCommand command) {
        AccountEntity account = requireAccount(command.accountId());
        InstrumentEntity instrument = requireInstrument(command.instrumentId());
        TransactionEntity entity = new TransactionEntity();
        apply(entity, command);
        TransactionEntity saved = transactions.saveAndFlush(entity);
        assertPositionValid(command.accountId(), command.instrumentId());
        return view(saved, account, instrument);
    }

    @Transactional
    public TransactionView update(UUID id, TransactionCommand command) {
        TransactionEntity entity = find(id);
        AccountEntity account = requireAccount(command.accountId());
        InstrumentEntity instrument = requireInstrument(command.instrumentId());
        UUID oldAccountId = entity.getAccountId();
        UUID oldInstrumentId = entity.getInstrumentId();

        apply(entity, command);
        transactions.saveAndFlush(entity);

        assertPositionValid(command.accountId(), command.instrumentId());
        if (!oldAccountId.equals(command.accountId())
                || !oldInstrumentId.equals(command.instrumentId())) {
            assertPositionValid(oldAccountId, oldInstrumentId);
        }
        return view(entity, account, instrument);
    }

    @Transactional
    public void delete(UUID id) {
        TransactionEntity entity = find(id);
        transactions.delete(entity);
        transactions.flush();
        assertPositionValid(entity.getAccountId(), entity.getInstrumentId());
    }

    private void assertPositionValid(UUID accountId, UUID instrumentId) {
        replayFor(accountId, instrumentId)
                .violation()
                .ifPresent(
                        violation -> {
                            throw new OversellException(
                                    violation.message(), violation.transactionId());
                        });
    }

    private TransactionEntity find(UUID id) {
        return transactions
                .findById(id)
                .orElseThrow(
                        () -> new NotFoundException("Transaction %s does not exist".formatted(id)));
    }

    private AccountEntity requireAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ValidationException("accountId", "Account does not exist"));
    }

    private InstrumentEntity requireInstrument(UUID id) {
        return instruments
                .findById(id)
                .orElseThrow(
                        () -> new ValidationException("instrumentId", "Instrument does not exist"));
    }

    /** Validates the field rules for the transaction type and copies them onto the entity. */
    private static void apply(TransactionEntity entity, TransactionCommand c) {
        entity.setAccountId(c.accountId());
        entity.setInstrumentId(c.instrumentId());
        entity.setType(c.type());
        entity.setTradeDate(c.tradeDate());
        entity.setNote(c.note() == null || c.note().isBlank() ? null : c.note().trim());
        if (c.type() == TxnType.SPLIT) {
            forbid("quantity", c.quantity(), "A split has no quantity");
            forbid("unitPrice", c.unitPrice(), "A split has no price");
            requirePositive("splitNumerator", c.splitNumerator());
            requirePositive("splitDenominator", c.splitDenominator());
            entity.setQuantity(null);
            entity.setUnitPrice(null);
            entity.setSplitNumerator(c.splitNumerator());
            entity.setSplitDenominator(c.splitDenominator());
        } else {
            forbid("splitNumerator", c.splitNumerator(), "Only a split has a ratio");
            forbid("splitDenominator", c.splitDenominator(), "Only a split has a ratio");
            BigDecimal quantity = decimal("quantity", c.quantity());
            if (quantity.signum() <= 0) {
                throw new ValidationException("quantity", "Quantity must be greater than zero");
            }
            BigDecimal unitPrice = decimal("unitPrice", c.unitPrice());
            if (unitPrice.signum() < 0) {
                throw new ValidationException("unitPrice", "Price must be zero or more");
            }
            entity.setQuantity(quantity);
            entity.setUnitPrice(unitPrice);
            entity.setSplitNumerator(null);
            entity.setSplitDenominator(null);
        }
    }

    private static void forbid(String field, Object value, String message) {
        if (value != null) {
            throw new ValidationException(field, message);
        }
    }

    private static void requirePositive(String field, Integer value) {
        if (value == null || value <= 0) {
            throw new ValidationException(field, "A split needs a positive ratio");
        }
    }

    private static BigDecimal decimal(String field, BigDecimal value) {
        if (value == null) {
            throw new ValidationException(field, "A value is required");
        }
        BigDecimal scaled = value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (scaled.precision() - scaled.scale() > MAX_INTEGER_DIGITS) {
            throw new ValidationException(field, "The value is too large");
        }
        return scaled;
    }

    private static Specification<TransactionEntity> matching(TransactionFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.accountId() != null) {
                predicates.add(builder.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter.instrumentId() != null) {
                predicates.add(builder.equal(root.get("instrumentId"), filter.instrumentId()));
            }
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("type"), filter.type()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("tradeDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("tradeDate"), filter.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static TransactionView view(
            TransactionEntity t, AccountEntity account, InstrumentEntity instrument) {
        return new TransactionView(
                t.getId(),
                t.getSeq(),
                t.getAccountId(),
                account == null ? null : account.getName(),
                t.getInstrumentId(),
                instrument == null ? null : instrument.getSymbol(),
                t.getType(),
                t.getTradeDate(),
                t.getQuantity(),
                t.getUnitPrice(),
                t.getSplitNumerator(),
                t.getSplitDenominator(),
                t.getNote());
    }
}
