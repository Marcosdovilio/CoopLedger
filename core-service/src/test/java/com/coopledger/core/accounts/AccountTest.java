package com.coopledger.core.accounts;

import static org.assertj.core.api.Assertions.*;
import com.coopledger.shared.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {
    @Test void opensAtZero() {
        Account account = new Account("alice");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }
    @Test void debitsExactly() {
        Account account = new Account("alice");
        account.credit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("30.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("70.00");
    }
    @Test void refusesOverdraftWithoutChangingBalance() {
        Account account = new Account("alice");
        account.credit(new BigDecimal("100.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01"))).isInstanceOf(DomainException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }
    @Test void blockedAccountsCannotMoveMoney() {
        Account account = new Account("alice");
        account.changeStatus(Account.Status.BLOCKED);
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> account.debit(BigDecimal.ONE)).isInstanceOf(DomainException.class);
    }
    @Test void preventsOverflowWithoutChangingBalance() {
        Account account = new Account("alice");
        account.credit(Money.MAX);
        assertThatThrownBy(() -> account.credit(new BigDecimal("0.01"))).isInstanceOf(DomainException.class);
        assertThat(account.getBalance()).isEqualTo(Money.MAX);
    }
}
