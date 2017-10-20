package org.dustinl.asynchttpclient.filter

import org.asynchttpclient.*
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class DegradingRequestFilterTest extends Specification {

    def "test connect timeout or refuse"() {
        setup:
        DegradingRequestFilter filter = new DegradingRequestFilter(Collections.emptyList())
        AsyncHttpClientConfig cf = new DefaultAsyncHttpClientConfig.Builder()
                .addRequestFilter(filter) .setConnectTimeout(10).build()
        AsyncHttpClient client = new DefaultAsyncHttpClient(cf)

        when:
        def future = client.prepareGet(url).execute()
        future.get()

        then:
        ExecutionException e = thrown()
        e.getCause() instanceof ConnectException

        where:
        url << ['https://tw.yahoo.com:8888', 'http://localhost:6669']
    }

    def "test request timeout"() {
        setup:
        List<DegradeTracker> trackers = new ArrayList<>()
        Collections.addAll(trackers, new DegradeTracker(URI.create(url).getHost(), 1, 1, 1))
        DegradingRequestFilter filter = new DegradingRequestFilter(trackers)
        AsyncHttpClientConfig cf = new DefaultAsyncHttpClientConfig.Builder()
                .addRequestFilter(filter) .setConnectTimeout(1000).setMaxRequestRetry(0).build()
        AsyncHttpClient client = new DefaultAsyncHttpClient(cf)
        Request request = new RequestBuilder().setUrl(url).setRequestTimeout(100).build()

        when:
        def future = client.executeRequest(request)
        def tracker = filter.trackerMap.get(URI.create(url).getHost())
        future.get()

        then:
        tracker.getErrorSize() == 1
        ExecutionException e = thrown()
        e.getCause() instanceof TimeoutException

        where:
        url = 'http://slowwly.robertomurray.co.uk/delay/3000/url/http://www.google.com'
    }
}
