package org.dustinl.asynchttpclient.filter;

import com.google.common.collect.ImmutableMap;
import io.netty.channel.Channel;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.filter.FilterContext;
import org.asynchttpclient.filter.FilterException;
import org.asynchttpclient.filter.RequestFilter;
import org.asynchttpclient.handler.AsyncHandlerExtensions;
import org.asynchttpclient.netty.request.NettyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The type Degrading request filter.
 */
public class DegradingRequestFilter implements RequestFilter {
    static final Logger logger = LoggerFactory.getLogger(DegradingRequestFilter.class);
    static final int SERVER_TIMEOUT = 524;
    static final int SERVER_UNREACHABLE = 523;

    private final Map<String, DegradeTracker> trackerMap;

    /**
     * Instantiates a new Degrading request filter.
     *
     * @param trackers the {@link List} of degraing trackers
     */
    public DegradingRequestFilter(List<DegradeTracker> trackers) {
        trackerMap = ImmutableMap.copyOf(
                trackers.stream().collect(Collectors.toMap(DegradeTracker::getHostname, Function.identity()))
        );
    }

    @Override
    public <T> FilterContext<T> filter(FilterContext<T> ctx) throws FilterException {
        final URI uri = URI.create(ctx.getRequest().getUrl());
        String hostname = uri.getHost();
        if (trackerMap.containsKey(hostname)) {
            DegradeTracker tracker = trackerMap.get(hostname);
            if (tracker.isDegraded()) {
                throw new ServerDegradedException(String.format(
                        "stop process request, server %s has been degraded", hostname)
                );
            }
            return new FilterContext.FilterContextBuilder<>(ctx)
                    .asyncHandler(new AsyncHandlerWrapper<>(ctx.getAsyncHandler(), tracker)).build();
        }
        return ctx;
    }

    private static class AsyncHandlerWrapper<T> implements AsyncHandler<T>, AsyncHandlerExtensions {
        private final AsyncHandler<T> asyncHandler;
        private final AsyncHandlerExtensions extensions;
        private DegradeTracker tracker;

        AsyncHandlerWrapper(final AsyncHandler<T> asyncHandler, DegradeTracker tracker) {
            this.asyncHandler = asyncHandler;
            this.tracker = tracker;
            extensions = (asyncHandler instanceof AsyncHandlerExtensions)
                    ? (AsyncHandlerExtensions) asyncHandler : null;
        }

        @Override
        public void onThrowable(Throwable t) {
            logger.debug("onThrowable get exception: {}", t.toString());
            try {
                if (t instanceof ConnectException) {
                    tracker.addResult(SERVER_UNREACHABLE);
                } else if (t instanceof TimeoutException) {
                    tracker.addResult(SERVER_TIMEOUT);
                }
            } catch (Exception e) {
                logger.warn(
                       "DegradingFilter got a exception, degrading might not worked, but the request will continue: {}",
                        e.toString());
            } finally {
                asyncHandler.onThrowable(t);
            }
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
            return asyncHandler.onBodyPartReceived(bodyPart);
        }

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
            logger.debug("receive status {}", responseStatus.getStatusCode());
            try {
                tracker.addResult(responseStatus.getStatusCode());
            } catch (Exception e) {
                logger.warn(
                       "DegradingFilter got a exception, degrading might not worked, but the request will continue: {}",
                        e.toString());
            }
            return asyncHandler.onStatusReceived(responseStatus);
        }

        @Override
        public State onHeadersReceived(HttpResponseHeaders headers) throws Exception {
            return asyncHandler.onHeadersReceived(headers);
        }

        @Override
        public T onCompleted() throws Exception {
            return asyncHandler.onCompleted();
        }

        @Override
        public void onHostnameResolutionAttempt(String name) {
            if (extensions != null) {
                extensions.onHostnameResolutionAttempt(name);
            }
        }

        @Override
        public void onHostnameResolutionSuccess(String name, List<InetSocketAddress> addresses) {
            if (extensions != null) {
                extensions.onHostnameResolutionSuccess(name, addresses);
            }
        }

        @Override
        public void onHostnameResolutionFailure(String name, Throwable cause) {
            if (extensions != null) {
                extensions.onHostnameResolutionFailure(name, cause);
            }
        }

        @Override
        public void onTcpConnectAttempt(InetSocketAddress remoteAddress) {
            if (extensions != null) {
                extensions.onTcpConnectAttempt(remoteAddress);
            }
        }

        @Override
        public void onTcpConnectSuccess(InetSocketAddress remoteAddress, Channel connection) {
            if (extensions != null) {
                extensions.onTcpConnectSuccess(remoteAddress, connection);
            }
        }

        @Override
        public void onTcpConnectFailure(InetSocketAddress remoteAddress, Throwable cause) {
            if (extensions != null) {
                extensions.onTcpConnectFailure(remoteAddress, cause);
            }
        }

        @Override
        public void onTlsHandshakeAttempt() {
            if (extensions != null) {
                extensions.onTlsHandshakeAttempt();
            }
        }

        @Override
        public void onTlsHandshakeSuccess() {
            if (extensions != null) {
                extensions.onTlsHandshakeSuccess();
            }
        }

        @Override
        public void onTlsHandshakeFailure(Throwable cause) {
            if (extensions != null) {
                extensions.onTlsHandshakeFailure(cause);
            }
        }

        @Override
        public void onConnectionPoolAttempt() {
            if (extensions != null) {
                extensions.onConnectionPoolAttempt();
            }
        }

        @Override
        public void onConnectionPooled(Channel connection) {
            if (extensions != null) {
                extensions.onConnectionPooled(connection);
            }
        }

        @Override
        public void onConnectionOffer(Channel connection) {
            if (extensions != null) {
                extensions.onConnectionOffer(connection);
            }
        }

        @Override
        public void onRequestSend(NettyRequest request) {
            if (extensions != null) {
                extensions.onRequestSend(request);
            }
        }

        @Override
        public void onRetry() {
            if (extensions != null) {
                extensions.onRetry();
            }
        }
    }
}
