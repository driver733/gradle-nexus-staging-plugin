package io.codearte.gradle.nexus.logic

import io.codearte.gradle.nexus.exception.RepositoryInTransitionException
import io.codearte.gradle.nexus.infra.WrongNumberOfRepositories
import spock.lang.Specification

class OperationRetrierSpec extends Specification {

    private static final String TEST_PROFILE_ID = "profileId"

    private OperationRetrier<String> retrier

    void setup() {
        retrier = new OperationRetrier<String>(2, 0)
    }

    def "should retry operation and pass returned value on #exceptionToThrow.class.simpleName"() {
        given:
            RepositoryFetcher fetcherMock = Mock()
            int counter = 0
        when:
            String returnedValue = retrier.doWithRetry {
                fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED)
            }
        then:
            2 * fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) >> {
                if (counter++ == 0) {
                    throw exceptionToThrow
                } else {
                    return "valueToReturn"
                }
            }
        and:
            returnedValue == "valueToReturn"
        where:
            exceptionToThrow << [new WrongNumberOfRepositories(0, "open"),
                                 new RepositoryInTransitionException('repoId', 'open')]
    }

    def "should propagate original exception on too many retry attempts"() {
        given:
            RepositoryFetcher fetcherMock = Mock()
        when:
            retrier.doWithRetry { fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) }
        then:
            3 * fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) >> {
                throw new WrongNumberOfRepositories(0, "closed")
            }
        and:
            thrown(WrongNumberOfRepositories)
    }

    def "should fail immediately on other exception"() {
        given:
            RepositoryFetcher fetcherMock = Mock()
        when:
            retrier.doWithRetry { fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) }
        then:
            1 * fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) >> {
                throw new NullPointerException()
            }
        and:
            thrown(NullPointerException)
    }

    def "should honor delay between retries"() {
        given:
            OperationRetrier spiedRetrier = Spy()
        and:
            def fetcherMock = Mock(RepositoryFetcher)
            int counter = 0
        when:
            String returnedValue = spiedRetrier.doWithRetry { fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) }
        then:
            1 * spiedRetrier.waitBeforeNextAttempt() >> { /* do nothing */ }
        and:
            2 * fetcherMock.getRepositoryIdWithGivenStateForStagingProfileId(TEST_PROFILE_ID, RepositoryState.CLOSED) >> {
                if (counter++ == 0) {
                    throw new WrongNumberOfRepositories(0, "closed")
                } else {
                    return "repoId"
                }
            }
        and:
            returnedValue == "repoId"
    }
}
