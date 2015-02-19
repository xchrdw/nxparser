package org.semanticweb.yars.nx.parser;


import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.semanticweb.yars.nx.Node;

public class NxParserTest {

    @Test
    public void test() throws ParseException {
        Node[] nodes = NxParser.parseNodes("<http://a> <http://p> <http://b> .");
        checkNodes(nodes, "http://a", "http://p", "http://b");
    }

    @Test
    public void testTabsWithUris() throws ParseException {
        Node[] nodes = NxParser.parseNodes("<http://a>\t<http://p>\t<http://b>.");
        checkNodes(nodes, "http://a", "http://p", "http://b");
    }

    @Test
    public void testString() throws ParseException {
        Node[] nodes = NxParser.parseNodes("<http://a> <http://p> \"test\"@en.");
        checkNodes(nodes, "http://a", "http://p", "test");
    }

    private void checkNodes(Node[] nodes, String... expected) {
        assertEquals(expected.length, nodes.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Node " + i + "", expected[i], nodes[i].toString());
        }
    }

}
