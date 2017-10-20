package org.dustinl.asynchttpclient.filter

import spock.lang.Specification

import java.time.Clock
import java.util.concurrent.TimeUnit

class DegradeTrackerTest extends Specification {

    def "degraded"() {
        when:
        DegradeTracker tracker = new DegradeTracker('fdf', errorWindow, maxError, degragePeriod, )
        5.times { tracker.addResult(status) }

        then:
        tracker.isDegraded()

        where:
        errorWindow = 10
        maxError = 5
        degragePeriod = 10
        status << [DegradingRequestFilter.SERVER_UNREACHABLE, DegradingRequestFilter.SERVER_TIMEOUT]
    }

    def "not enough error"() {
        when:
        DegradeTracker tracker = new DegradeTracker('fdf', errorWindow, maxError, degragePeriod)
        9.times { tracker.addResult(status) }

        then:
        !tracker.isDegraded()

        where:
        errorWindow = 10
        maxError = 10
        degragePeriod = 10
        status << [DegradingRequestFilter.SERVER_UNREACHABLE, DegradingRequestFilter.SERVER_TIMEOUT]
    }

    def "not in error window"() {
        setup:
        def mockClock = Mock(Clock)
        mockClock.millis() >>> (1..4).toList() +
                TimeUnit.SECONDS.toMillis(errorWindow + 1) + TimeUnit.SECONDS.toMillis(degragePeriod - 1)

        DegradeTracker tracker = new DegradeTracker('fdf', errorWindow, maxError, degragePeriod, mockClock)
        4.times { tracker.addResult(status) }
        tracker.addResult(status)

        expect:
        !tracker.isDegraded()

        where:
        errorWindow = 1
        maxError = 5
        degragePeriod = 10
        status << [DegradingRequestFilter.SERVER_UNREACHABLE, DegradingRequestFilter.SERVER_TIMEOUT]
    }

    def "degrade period expired"() {
        setup:
        def mockClock = Mock(Clock)
        mockClock.millis() >>> (1..6).toList() + TimeUnit.SECONDS.toMillis(degragePeriod + 1)

        DegradeTracker tracker = new DegradeTracker('fdf', errorWindow, maxError, degragePeriod, mockClock)
        5.times { tracker.addResult(status) }

        expect:
        tracker.isDegraded()
        !tracker.isDegraded()

        where:
        errorWindow = 1
        maxError = 5
        degragePeriod = 10
        status << [DegradingRequestFilter.SERVER_UNREACHABLE, DegradingRequestFilter.SERVER_TIMEOUT]
    }

    def "recover from degraded, and degrade again"() {
        when:
        def mockClock = Mock(Clock)
        mockClock.millis() >>> (1..22).toList()
        DegradeTracker tracker = new DegradeTracker('fdf', errorWindow, maxError, degragePeriod, mockClock)
        (0..5).each { tracker.addResult(status) }
        tracker.isDegraded()
        (0..5).each { tracker.addResult(status) }

        then:
        tracker.isDegraded()

        where:
        errorWindow = 10
        maxError = 5
        degragePeriod = 1
        status << [DegradingRequestFilter.SERVER_UNREACHABLE, DegradingRequestFilter.SERVER_TIMEOUT]
    }
}
