package at.htlle.freq.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

final class TransactionTestUtils {

    private TransactionTestUtils() {
    }

    static List<TransactionSynchronization> executeWithinTransaction(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            return new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
