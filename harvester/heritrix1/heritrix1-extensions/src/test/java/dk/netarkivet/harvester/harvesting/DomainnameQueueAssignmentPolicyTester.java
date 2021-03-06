/*
 * #%L
 * Netarchivesuite - harvester - test
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.harvester.harvesting;

import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.datamodel.CandidateURI;
import org.archive.net.UURI;
import org.junit.Test;

import dk.netarkivet.common.exceptions.ArgumentNotValid;

@SuppressWarnings({"serial"})
public class DomainnameQueueAssignmentPolicyTester {
    /**
     * A key used for the cases when we can't figure out the URI. This is taken from parent, where it has private
     * access. Parent returns this on things like about:blank.
     */
    static final String DEFAULT_CLASS_KEY = "default...";

    @Test
    public void testGetClassKey() throws Exception {
        // Check that domain names + port numbers are extracted as expected
        assertEquals("Should find domain name from simple URL", "foo.dk", getDomainName("http://www.foo.dk"));
        assertEquals("Should find domain name for more complex URL", "bar.dk",
                getDomainName("http://x.y.bar.dk:8081/checkMe"));
        assertEquals("Should remove protocol and path from two-level URL", "baz.dk",
                getDomainName("http://baz.dk/fnord"));
        assertEquals("Should be able to handle numeric domain names", "911.dk", getDomainName("http://20.911.dk"));
        assertEquals("Should find IP address from URL", "192.168.0.10", getDomainName("https://192.168.0.10:20/foo"));
        assertEquals("Should not attempt to parse IP-like URL as IP", "11.dk", getDomainName("http://192.168.0.11.dk"));
        assertEquals("Should not attempt to parse IP-like URL as IP", "12.dk", getDomainName("http://168.0.12.dk:192"));
        // As of July 18th 'barbar' is not a public suffix but 'bar' is:  
        // https://www.publicsuffix.org/list/public_suffix_list.dat
        String illegalHostname = "x.fnord.barbar";
        assertEquals("Should return original key on illegal hostname", illegalHostname,
                getDomainName("http://" + illegalHostname));
        assertEquals("Should get domain name for DNS request", "foo.dk", getDomainName("dns:bar.foo.dk"));
        // See bug 649 for this test.
        assertEquals("Should return default key on illegal scheme", DEFAULT_CLASS_KEY, getDomainName("about:blank"));

    }

    @Test
    public void testGetClassKeyPartTwo() {

        DomainnameQueueAssignmentPolicy policy = new DomainnameQueueAssignmentPolicy();
        assertEquals("Should return default key on empty scheme", DEFAULT_CLASS_KEY,
                policy.getClassKey(null, getCandidateURI("")));
        assertEquals("Should return default key on hash scheme", DEFAULT_CLASS_KEY,
                policy.getClassKey(null, getCandidateURI("#")));
        assertEquals("Should return default key on null scheme", DEFAULT_CLASS_KEY, policy.getClassKey(null, null));
        assertEquals("Should return default key on triple scheme", DEFAULT_CLASS_KEY,
                policy.getClassKey(null, getCandidateURI("foo.dk#1010#fnord")));
    }

    @Test
    public void testTopLevelDomains() throws URIException {
        assertEquals("free.fr", getDomainName("http://test.free.fr"));
        assertEquals("test.asso.fr", getDomainName("http://test.asso.fr"));
    }

    /**
     * Create an arbitrarily bogus CandidateURI. As constructor "new UURI("", true)" is no longer visible
     */
    private CandidateURI getCandidateURI(String s) {
        return new CandidateURI() {
            public UURI getUURI() {
                try {
                    return new FixedUURI("", true);
                } catch (URIException e) {
                    throw new ArgumentNotValid("Empty URL", e);
                }
            }

            public UURI getVia() {
                return null;
            }
        };
    }

    /**
     * Get the domain name part of a string same way as the harvester does.
     *
     * @param s
     * @return the domain name part of a string same way as the harvester does.
     * @throws URIException
     */
    private String getDomainName(String s) throws URIException {
        DomainnameQueueAssignmentPolicy policy = new DomainnameQueueAssignmentPolicy();
        return policy.getClassKey(null, CandidateURI.fromString(s));
    }
}
