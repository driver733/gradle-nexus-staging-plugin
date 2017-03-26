package io.codearte.gradle.nexus.logic

import com.google.common.annotations.VisibleForTesting
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import io.codearte.gradle.nexus.exception.RepositoryInTransitionException
import io.codearte.gradle.nexus.infra.WrongNumberOfRepositories

@Slf4j
class OperationRetrier<T> {

    public static final int DEFAULT_NUMBER_OF_RETRIES = 7
    public static final int DEFAULT_DELAY_BETWEEN_RETRIES_IN_MILLIS = 1000

    private final int numberOfRetries
    private final int delayBetweenRetriesInMillis

    OperationRetrier(int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES, int delayBetweenRetriesInMillis = DEFAULT_DELAY_BETWEEN_RETRIES_IN_MILLIS) {
        this.numberOfRetries = numberOfRetries
        this.delayBetweenRetriesInMillis = delayBetweenRetriesInMillis
    }

    T doWithRetry(Closure<T> operation) {
        int counter = 0
        int numberOfAttempts = numberOfRetries + 1
        while (true) {
            try {
                counter++
                log.debug("Attempt $counter/$numberOfAttempts...")
                return operation()
            } catch (WrongNumberOfRepositories | RepositoryInTransitionException | IllegalArgumentException e) { //Exceptions to catch could be configurable if needed
                String message = "Attempt $counter/$numberOfAttempts failed. ${e.getClass().getSimpleName()} was thrown with message '${e.message}'"
                if (counter >= numberOfAttempts) {
                    //TODO: Switch to Gradle logger and use lifecycle level
                    log.warn("$message. Giving up. Configure longer timeout if necessary.")
                    throw e
                } else {
                    if (counter == 1) {
                        //TODO: Switch to Gradle logger and use lifecycle level
                        log.warn("Requested operation failed in first try. Retrying for maximum ${formatMaximumRetryingTime()}.")
                    }
                    log.info("$message. Waiting $delayBetweenRetriesInMillis ms before next retry.")
                    waitBeforeNextAttempt()
                }
            }
        }
    }

    @VisibleForTesting
    @PackageScope
    void waitBeforeNextAttempt() {
        //sleep() hangs the thread, but in that case it doesn't matter - switch to https://github.com/nurkiewicz/async-retry/ if really needed
        sleep(delayBetweenRetriesInMillis)
    }

    private String formatMaximumRetryingTime() {
        return "${numberOfRetries * delayBetweenRetriesInMillis / 1000} seconds"
    }
}
