/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jnlp.converter.parser.xml;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.io.IOException;
import java.util.Stack;

public class XMLParser extends DefaultHandler {

    private XMLNode _root;
    private final String _source;
    private Stack<XMLNode> _inProgress;
    private String _characters;

    // although defined in com.sun.org.apache.xerces.internal.impl.Constants,
    // we should not be able to access that, so defined here
    private final static String DTD_DOWNLOAD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final SAXParserFactory SPF = SAXParserFactory.newInstance();

    /*
     * Construct an <code>XMLParser</code>.
     *
     * @param source  - the source text to parse.
     */
    public XMLParser(String source) {
        _source = source.trim();
    }

    public XMLNode parse() throws SAXException {
        // normally we parse without validating, but leave option to parse
        // with validation, possibly controlled by config option.
        return parse(false);
    }

    public XMLNode parse(boolean validating) throws SAXException {
        _root = null;
        _inProgress = new Stack<>();

        try {
            InputSource is = new InputSource(new StringReader(_source));
            SPF.setValidating(validating);
            // only download dtd file from DOCTYPE if we are doing validation
            try {
                SPF.setFeature(DTD_DOWNLOAD, validating);
            } catch (Exception e) {
            }
            SAXParser sp = SPF.newSAXParser();
            sp.parse(is, this);
        } catch (ParserConfigurationException | IOException pce) {
            throw new SAXException(pce);
        }

        return _root;
    }

    @Override
    public void startElement(String uri, String localeName, String qName,
            Attributes attributes) throws SAXException {

        XMLAttribute first = null;
        XMLAttribute last = null;
        int len = attributes.getLength();

        for (int i = 0; i < len; i++) {
            XMLAttribute att = new XMLAttribute(
                    // in old implementation attribute names and values were trimmed
                    attributes.getQName(i).trim(), attributes.getValue(i).trim());
            if (first == null) {
                first = att;
            }
            if (last != null) {
                last.setNext(att);
            }
            last = att;
        }
        _inProgress.push(new XMLNode(qName, first));
        _characters = null;
    }

    @Override
    public void endElement(String uri, String localeName, String elementName)
            throws SAXException {
        XMLNode node = _inProgress.pop();
        // <information>
        //  <title>Title</title>
        //  <vendor>Vendor</vendor> "Some whitespaces"
        // </information>
        // In example above when we receive end of <information> we will
        // have _characters set to whitespace and new line and it will be
        // added as child node to <information>. This will break our cache code
        // which will think that JNLP file changed on server even if it is not
        // and thus we might not load properly.
        //
        // <application-desc name="HelloWorld" main-class="HelloWorld">
        //  <argument>  test with whitespaces </argument>
        // </application-desc>
        // From example above we want to include whitespaces for <argument>.
        //
        // <node1>
        //  <node2>abc</node2>
        //  xyz (might be whitespaces)
        // </node1>
        // In JNLP spec we do not have cases when node have nested nodes as
        // well as text which is whitespaces only.
        //
        // So to fix it lets check if ending node have nested nodes, then do
        // not add whitespaces only node.
        if (node != null && node.getNested() != null && _characters != null) {
            String trimCharacters = _characters.trim();
            if ((trimCharacters == null) || (trimCharacters.length() == 0)) {
                _characters = null; // No need to add whitespaces only
            }
        }
        if ((_characters != null) && (_characters.trim().length() > 0)) {
            addChild(node, new XMLNode(_characters));
        }

        if (_inProgress.isEmpty()) {
            _root = node;
        } else {
            addChild(_inProgress.peek(), node);
        }
        _characters = null;
    }

    @Override
    public void characters(char[] chars, int start, int length)
            throws SAXException {
        String s = new String(chars, start, length);
        _characters = ((_characters == null) ? s : _characters + s);
    }

    @Override
    public void ignorableWhitespace(char[] chars, int start, int length)
            throws SAXException {
        String s = new String(chars, start, length);
        _characters = ((_characters == null) ? s : _characters + s);
    }

    private void addChild(XMLNode parent, XMLNode child) {
        child.setParent(parent);

        XMLNode sibling = parent.getNested();
        if (sibling == null) {
            parent.setNested(child); // set us as only child
        } else {
            while (sibling.getNext() != null) {
                sibling = sibling.getNext();
            }
            sibling.setNext(child); // sets us as youngest child
        }
    }
}
