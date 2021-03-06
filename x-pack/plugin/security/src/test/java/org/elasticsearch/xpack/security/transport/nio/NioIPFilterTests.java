/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.transport.nio;

import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.network.InetAddresses;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.BoundTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.nio.NioSocketChannel;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.xpack.security.audit.AuditTrailService;
import org.elasticsearch.xpack.security.transport.filter.IPFilter;
import org.junit.Before;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NioIPFilterTests extends ESTestCase {

    private NioIPFilter nioIPFilter;

    @Before
    public void init() throws Exception {
        Settings settings = Settings.builder()
            .put("xpack.security.transport.filter.allow", "127.0.0.1")
            .put("xpack.security.transport.filter.deny", "10.0.0.0/8")
            .build();

        boolean isHttpEnabled = randomBoolean();

        Transport transport = mock(Transport.class);
        TransportAddress address = new TransportAddress(InetAddress.getLoopbackAddress(), 9300);
        when(transport.boundAddress()).thenReturn(new BoundTransportAddress(new TransportAddress[] { address }, address));
        when(transport.lifecycleState()).thenReturn(Lifecycle.State.STARTED);
        ClusterSettings clusterSettings = new ClusterSettings(Settings.EMPTY, new HashSet<>(Arrays.asList(
            IPFilter.HTTP_FILTER_ALLOW_SETTING,
            IPFilter.HTTP_FILTER_DENY_SETTING,
            IPFilter.IP_FILTER_ENABLED_HTTP_SETTING,
            IPFilter.IP_FILTER_ENABLED_SETTING,
            IPFilter.TRANSPORT_FILTER_ALLOW_SETTING,
            IPFilter.TRANSPORT_FILTER_DENY_SETTING,
            IPFilter.PROFILE_FILTER_ALLOW_SETTING,
            IPFilter.PROFILE_FILTER_DENY_SETTING)));
        XPackLicenseState licenseState = mock(XPackLicenseState.class);
        when(licenseState.isIpFilteringAllowed()).thenReturn(true);
        AuditTrailService auditTrailService = new AuditTrailService(Collections.emptyList(), licenseState);
        IPFilter ipFilter = new IPFilter(settings, auditTrailService, clusterSettings, licenseState);
        ipFilter.setBoundTransportAddress(transport.boundAddress(), transport.profileBoundAddresses());
        if (isHttpEnabled) {
            HttpServerTransport httpTransport = mock(HttpServerTransport.class);
            TransportAddress httpAddress = new TransportAddress(InetAddress.getLoopbackAddress(), 9200);
            when(httpTransport.boundAddress()).thenReturn(new BoundTransportAddress(new TransportAddress[] { httpAddress }, httpAddress));
            when(httpTransport.lifecycleState()).thenReturn(Lifecycle.State.STARTED);
            ipFilter.setBoundHttpTransportAddress(httpTransport.boundAddress());
        }

        if (isHttpEnabled) {
            nioIPFilter = new NioIPFilter(ipFilter, IPFilter.HTTP_PROFILE_NAME);
        } else {
            nioIPFilter = new NioIPFilter(ipFilter, "default");
        }
    }

    public void testThatFilteringWorksByIp() throws Exception {
        InetSocketAddress localhostAddr = new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 12345);
        NioSocketChannel channel1 = mock(NioSocketChannel.class);
        when(channel1.getRemoteAddress()).thenReturn(localhostAddr);
        assertThat(nioIPFilter.test(channel1), is(true));

        InetSocketAddress remoteAddr = new InetSocketAddress(InetAddresses.forString("10.0.0.8"), 12345);
        NioSocketChannel channel2 = mock(NioSocketChannel.class);
        when(channel2.getRemoteAddress()).thenReturn(remoteAddr);
        assertThat(nioIPFilter.test(channel2), is(false));
    }
}
