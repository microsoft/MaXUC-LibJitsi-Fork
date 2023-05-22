/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util.xml;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
import static javax.xml.XMLConstants.XML_NS_URI;
import static org.jitsi.util.StringUtils.fromString;
import static org.jitsi.util.StringUtils.isEquals;
import static org.jitsi.util.StringUtils.isNullOrEmpty;

import java.io.*;
import java.util.*;

import javax.xml.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.jitsi.util.*;
import org.w3c.dom.*;

/**
 * Common XML Tasks
 *
 * @author Emil Ivov
 * @author Damian Minkov
 */
public class XMLUtils
{
    /**
     * The <tt>Logger</tt> used by the <tt>XMLUtils</tt> class for logging
     * output.
     */
    private static final Logger logger = Logger.getLogger(XMLUtils.class);

    /**
     * XML Features to prevent XXE attacks.
     * See <a href="https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html">OWASP XML eXternal Entity injection prevention</a>
     */
    private static final String FEATURE_DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD_FEATURE =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * The unicode replacement character, used as a substitute for characters
     * which would be invalid in an XML file.
     */
    private static final String UNICODE_REPLACEMENT_CHAR =
                                        new String(Character.toChars(0xfffd));

    /**
     * String returned when data passed to be written to the xml is <code>null</code>.
     */
    private static final String NULL_STRING = "null";

    /**
     * Extracts from node the attribute with the specified name.
     * @param node the node whose attribute we'd like to extract.
     * @param name the name of the attribute to extract.
     * @return a String containing the trimmed value of the attribute or null
     * if no such attribute exists
     */
    public static String getAttribute(Node node, String name)
    {
        if (node == null)
            return null;

        Node attribute = node.getAttributes().getNamedItem(name);
        return (attribute == null)
                    ? null
                    : attribute.getNodeValue().trim();
    }

    /**
     * Extracts the String content of a TXT element.
     *
     * @param parentNode the node containing the data that we'd like to get.
     * @return the string contained by the node or null if none existed.
     */
    public static String getText(Element parentNode)
    {
        Text text = getTextNode(parentNode);

        return (text == null) ? null : text.getData();
    }

    /**
     * Sets data to be the TEXT content of element
     *
     * @param parentNode the parent element.
     * @param data the data to set.
     */
    public static void setText(Element parentNode, String data)
    {
        if(data == null)
            return;

        data = sanitize(data);

        Text txt = getTextNode(parentNode);

        if (txt != null)
            txt.setData(data);
        else
        {
            txt = parentNode.getOwnerDocument().createTextNode(data);
            parentNode.appendChild(txt);
        }
    }

    /**
     * Returns a copy of the given string, with all invalid characters removed
     * and replaced with the unicode substitution character 'fffffd'.
     *
     * Validity is determined as per the XML 1.0 specification.
     *
     * @param data The string in which to make the substitutions.
     * @return A copy of the string, with invalid characters replaced by
     * the unicode substitution character.
     */
    public static String sanitize(String data)
    {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < data.length(); )
        {
            int codePoint = data.codePointAt(offset);
            if (isPermittedCodePoint(codePoint))
            {
                result.append(Character.toChars(codePoint));
            }
            else
            {
                result.append(UNICODE_REPLACEMENT_CHAR);
            }

            offset += Character.charCount(codePoint);
        }

        return result.toString();
    }

    /**
     * See {@link XMLUtils#sanitize(String)}. In the case where the
     * input string is null, this returns {@link XMLUtils#NULL_STRING}.
     */
    public static String sanitizeNullable(String data)
    {
        return Optional.ofNullable(data)
                .map(XMLUtils::sanitize)
                .orElse(NULL_STRING);
    }

    /**
     * Sets an attribute on the given node, sanitizing the name and value of the
     * attribute by removing invalid characters.
     *
     * @param node The note on which to set the attribute.
     * @param name The attribute name.
     * @param value The value to which to set the attribute.
     */
    public static void setAttribute(Element node, String name, String value)
    {
        String sanitizedName = sanitizeNullable(name);
        String sanitizedValue = sanitizeNullable(value);
        node.setAttribute(sanitizedName, sanitizedValue);
    }

    /**
     * Checks whether the given integer specifies a unicode code point which is
     * permitted in an XML 1.0 document, as defined at
     * https://www.w3.org/TR/xml/#charsets and below:
     *
     * Char ::= #x9 | #xA | #xD |
     *          [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
     *
     *
     * @param codePoint The code point to test.
     * @return true if the code point is permissible; false otherwise.
     */
    public static boolean isPermittedCodePoint(int codePoint)
    {
        if (codePoint == 0x9)
        {
            // Horizontal tab.
            return true;
        }
        else if (codePoint == 0xa)
        {
            // Line feed.
            return true;
        }
        else if (codePoint == 0xd)
        {
            // Carriage return.
            return true;
        }
        else if (codePoint >= 0x20 && codePoint <= 0xd7ff)
        {
            // Characters from the basic multilingual plane, up to but not
            // including surrogate pair identifiers.
            return true;
        }
        else if (codePoint >= 0xe000 && codePoint <= 0xfffd)
        {
            // Remaining characters from the basic multilingual plane, following
            // surrogate pair identifiers and including the private use area,
            // but excluding the two non-characters at the end of the plane.
            return true;
        }
        else if (codePoint >= 0x10000 && codePoint <= 0x10ffff)
        {
            // Supplementary private-use characters permissible according to the
            // spec.
            return true;
        }
        else
        {
            // All other characters are forbidden.
            return false;
        }
    }

    /**
     * Sets data to be the CDATA content of element
     *
     * @param element the parent element.
     * @param data the data to set.
     */
    public static void setCData(Element element, String data)
    {
        if(data == null)
            return;

        CDATASection txt = getCDataNode(element);
        if (txt != null)
            txt.setData(data);
        else
        {
            txt = element.getOwnerDocument().createCDATASection(data);
            element.appendChild(txt);
        }
    }

    /**
     * Extract the CDATA content of the specified element.
     * @param element the element whose data we need
     * @return a String containing the CDATA value of element.
     */
    public static String getCData(Element element)
    {
        CDATASection text = getCDataNode(element);

        return (text == null) ? null : text.getData().trim();
    }

    /**
     * Returns element's CDATA child node (if it has one).
     * @param element the element whose CDATA we need to get.
     * @return a CDATASection object containing the specified element's CDATA
     * content
     */
    public static CDATASection getCDataNode(Element element)
    {
        return (CDATASection)getChildByType(element,
                                            Node.CDATA_SECTION_NODE);
    }

    /**
     * Returns element's TEXT child node (if it has one).
     * @param element the element whose TEXT we need to get.
     * @return a <tt>Text</tt> object containing the specified element's
     * text content.
     */
    public static Text getTextNode(Element element)
    {
        return (Text)getChildByType(element, Node.TEXT_NODE);
    }

    /**
     * Returns first of the <tt>element</tt>'s child nodes that is of type
     * <tt>nodeType</tt>.
     * @param element the element whose child we need.
     * @param nodeType the type of the child we need.
     * @return a child of the specified <tt>nodeType</tt> or null if none
     * was found.
     */
    public static Node getChildByType(Element element, short nodeType)
    {
        if (element == null)
            return null;

        NodeList nodes = element.getChildNodes();
        if (nodes == null || nodes.getLength() < 1)
            return null;

        Node node;
        String data;
        for (int i = 0; i < nodes.getLength(); i++)
        {
            node = nodes.item(i);
            short type = node.getNodeType();
            if (type == nodeType)
            {
                if (type == Node.TEXT_NODE ||
                    type == Node.CDATA_SECTION_NODE)
                {
                    data = ( (Text) node).getData();
                    if (data == null || data.trim().length() < 1)
                        continue;
                }

                return node;
            }
        }

        return null;
    }

    /**
     * Writes the specified document to the given file adding indentatation.
     * The default encoding is UTF-8.
     *
     * @param out the output File
     * @param document the document to write
     *
     * @throws java.io.IOException in case a TransformerException is thrown by
     * the underlying Transformer.
     */
    public static void writeXML(Document document, File out)
        throws java.io.IOException
    {
//        indentedWriteXML(document, new FileOutputStream(out));
        writeXML(document
                 , new StreamResult(
                        new OutputStreamWriter(
                                new FileOutputStream(out), "UTF-8"))
                 , null
                 , null);
    }

    /**
     * Writes the specified document to the given file adding indentatation.
     * The default encoding is UTF-8.
     *
     * @param writer the writer to use when writing the File
     * @param document the document to write
     *
     * @throws java.io.IOException in case a TransformerException is thrown by
     * the underlying Transformer.
     */
    public static void writeXML(Document document,
                                Writer   writer)
        throws java.io.IOException
    {
        writeXML(document, new StreamResult(writer), null, null);
    }

    /**
     * Writes the specified document to the given file adding indentatation.
     * The default encoding is UTF-8.
     *
     * @param streamResult the streamResult object where the document should be
     * written
     * @param document the document to write
     * @param doctypeSystem the doctype system of the xml document that we should
     * record in the file or null if none is specified.
     * @param doctypePublic the public identifier to be used in the document
     * type declaration.
     *
     * @throws java.io.IOException in case a TransformerException is thrown by
     * the underlying Transformer.
     */
    public static void writeXML(Document document,
                                StreamResult streamResult,
                                String   doctypeSystem,
                                String   doctypePublic)
        throws java.io.IOException
    {
        try
        {
           DOMSource domSource = new DOMSource(document);
           TransformerFactory tf = TransformerFactory.newInstance();

           // not working for jdk 1.4
           try
           {
                tf.setAttribute("indent-number", 4);
           }catch(Exception e){}

           Transformer serializer = tf.newTransformer();
           if(doctypeSystem != null)
                   serializer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                                                doctypeSystem);
            if(doctypePublic != null)
                   serializer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                                                doctypePublic);
           // not working for jdk 1.5
           serializer.setOutputProperty("{https://xml.apache.org/xalan}indent-amount", "4");
           serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
           serializer.setOutputProperty(OutputKeys.INDENT, "yes");
           serializer.transform(domSource, streamResult);
       }
        catch (TransformerException ex) {
            logger.error("Error saving configuration file", ex);
            throw new java.io.IOException(
                "Failed to write the configuration file: "
                + ex.getMessageAndLocation());
        }
        catch (IllegalArgumentException ex) {
            //this one is thrown by the setOutputProperty or in other words -
            //shoudln't happen. so let's just log it down in case ...
            logger.error("Error saving configuration file", ex);
        }
    }

    /**
     * A simple implementation of XML writing that also allows for indentation.
     * @param doc the Document that we will be writing.
     * @param out an OutputStream to write the document through.
     */
    public static void indentedWriteXML(Document doc, OutputStream out)
    {
        if (out != null)
        {
            try
            {
                Writer wri = new OutputStreamWriter(out, "UTF-8");
//                wri.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+lSep);
//                (new DOMElementWriter()).write(rootElement, wri, 0, "  ");
//                wri.flush();
//                wri.close();
                writeXML(doc
                 , new StreamResult(wri)
                 , null
                 , null);
                out.close();
            }
            catch (IOException exc)
            {
                throw new RuntimeException("Unable to write xml", exc);
            }
        }
    }

    /**
     * Whenever you'd need to print a configuration node and/or its children.
     *
     * @param root the root node to print.
     * @param out the print stream that should be used to outpu
     * @param recurse boolean
     * @param prefix String
     */
    public static void printChildElements(Element root,
                                          PrintStream out,
                                          boolean recurse,
                                          String prefix)
    {
        out.print(prefix + "<" + root.getNodeName());
        NamedNodeMap attrs = root.getAttributes();
        Node node;
        for(int i = 0; i < attrs.getLength(); i++)
        {
            node = attrs.item(i);
            out.print(" " + node.getNodeName() + "=\""
                      + node.getNodeValue() + "\"");
        }
        out.println(">");

        String data = getText(root);
        if(data != null && data.trim().length() > 0)
            out.println(prefix + "\t" + data);

        data = getCData(root);
        if(data != null && data.trim().length() > 0)
            out.println(prefix + "\t<![CDATA[" + data + "]]>");

        NodeList nodes = root.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++)
        {
            node = nodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE)
            {
                if(recurse)
                    printChildElements((Element)node, out, recurse, prefix
                                       + "\t");
                else
                    out.println(prefix + node.getNodeName());
            }
        }

        out.println(prefix + "</" + root.getNodeName() + ">");
    }

    /**
     * Returns the child element with the specified tagName for the specified
     * parent element.
     * @param parent The parent whose child we're looking for.
     * @param tagName the name of the child to find
     * @return The child with the specified name or null if no such child was
     *         found.
     * @throws NullPointerException if parent or tagName are null
     */
    public static Element findChild(Element parent, String tagName)
    {
        if(parent == null || tagName == null)
            throw new NullPointerException("Parent or tagname were null! "
                + "parent = " + parent + "; tagName = " + tagName);

        NodeList nodes = parent.getChildNodes();
        Node node;
        int len = nodes.getLength();
        for(int i = 0; i < len; i++)
        {
            node = nodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE
               && ((Element)node).getNodeName().equals(tagName))
                return (Element)node;
        }

        return null;
    }

    /**
     * Returns the children elements with the specified tagName for the
     * specified parent element.
     *
     * @param parent The parent whose children we're looking for.
     * @param tagName the name of the child to find
     * @return List of the children with the specified name
     * @throws NullPointerException if parent or tagName are null
     */
    public static List<Element> findChildren(Element parent, String tagName)
    {
        if (parent == null || tagName == null)
            throw new NullPointerException("Parent or tagname were null! "
                + "parent = " + parent + "; tagName = " + tagName);

        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        Node node;
        int len = nodes.getLength();
        for (int i = 0; i < len; i++)
        {
            node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element element = (Element) node;
                if (element.getNodeName().equals(tagName))
                    result.add(element);
            }
        }

        return result;
    }

    /**
     * Looks through all child elements of the specified root (recursively)
     * and returns the first element that corresponds to all parameters.
     *
     * @param root the Element where the search should begin
     * @param tagName the name of the node we're looking for
     * @param keyAttributeName the name of an attribute that the node has to
     * have
     * @param keyAttributeValue the value that attribute must have
     * @return the Element in the tree under root that matches the specified
     * parameters.
     * @throws NullPointerException if any of the arguments is null.
     */
    public static Element locateElement(Element root,
                                        String tagName,
                                        String keyAttributeName,
                                        String keyAttributeValue)
    {
        NodeList nodes = root.getChildNodes();
        int len = nodes.getLength();

        for(int i = 0; i < len; i++)
        {
            Node node = nodes.item(i);

            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            Element element = (Element) node;

            // is this the node we're looking for?
            if(node.getNodeName().equals(tagName))
            {
                String attr = element.getAttribute(keyAttributeName);

                if((attr != null) && attr.equals(keyAttributeValue))
                    return element;
            }

            //look inside.
            Element child
                = locateElement(
                        element,
                        tagName, keyAttributeName, keyAttributeValue);

            if (child != null)
                return child;
        }
        return null;
    }

    /**
     * Looks through all child elements of the specified root (recursively) and
     * returns the elements that corresponds to all parameters.
     *
     * @param root the Element where the search should begin
     * @param tagName the name of the node we're looking for
     * @param keyAttributeName the name of an attribute that the node has to
     *            have
     * @param keyAttributeValue the value that attribute must have
     * @return list of Elements in the tree under root that match the specified
     *         parameters.
     * @throws NullPointerException if any of the arguments is null.
     */
    public static List<Element> locateElements(Element root, String tagName,
        String keyAttributeName, String keyAttributeValue)
    {
        List<Element> result = new ArrayList<>();
        NodeList nodes = root.getChildNodes();
        Node node;
        int len = nodes.getLength();
        for (int i = 0; i < len; i++)
        {
            node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            // is this the node we're looking for?
            if (node.getNodeName().equals(tagName))
            {
                Element element = (Element) node;
                String attr = element.getAttribute(keyAttributeName);

                if (attr != null && attr.equals(keyAttributeValue))
                    result.add(element);
            }

            // look inside.

            List<Element> childs =
                locateElements((Element) node, tagName, keyAttributeName,
                    keyAttributeValue);

            if (childs != null)
                result.addAll(childs);
        }

        return result;
    }

    /**
     * Indicates whether namespace is one of the standart xml namespace.
     *
     * @param namespace the namespace to analyze.
     * @return true if namespace is one of the standart xml namespace otherwise
     *         false.
     */
    public static boolean isStandartXmlNamespace(String namespace)
    {
        namespace = normalizeNamespace(namespace);
        return normalizeNamespace(XML_NS_URI).equals(namespace)
                || normalizeNamespace(XMLNS_ATTRIBUTE_NS_URI).equals(namespace)
                || normalizeNamespace(W3C_XML_SCHEMA_NS_URI).equals(namespace)
                || normalizeNamespace(W3C_XML_SCHEMA_INSTANCE_NS_URI)
                .equals(namespace);
    }

    /**
     * Gets the node namespace.
     *
     * @param node the <tt>Element</tt> or <tt>Attr</tt> node to analyze.
     * @return the node namespace or null.
     */
    public static String getNamespaceUri(Node node)
    {
        String prefix = node.getPrefix();
        String namespaceUri = node.getNamespaceURI();

        if (!isNullOrEmpty(namespaceUri))
            return normalizeNamespace(namespaceUri);
        if (XMLConstants.XMLNS_ATTRIBUTE.equals(node.getNodeName())
                || XMLConstants.XMLNS_ATTRIBUTE.equals(prefix))
            return normalizeNamespace(XMLNS_ATTRIBUTE_NS_URI);

        Element rootElement = node.getOwnerDocument().getDocumentElement();
        Node parentNode = null;

        while (parentNode != rootElement)
        {
            if (parentNode == null)
            {
                if (node.getNodeType() == Node.ATTRIBUTE_NODE)
                {
                    parentNode = ((Attr) node).getOwnerElement();
                    // If attribute doesn't have prefix - it has its parent
                    // namespace
                    if (isNullOrEmpty(prefix))
                        prefix = parentNode.getPrefix();
                }
                else if (node.getNodeType() == Node.ELEMENT_NODE)
                    parentNode = node.getParentNode();
                else
                    return null;
            }
            else
                parentNode = parentNode.getParentNode();
            String parentPrefix = parentNode.getPrefix();
            String parentNamespaceUri = parentNode.getNamespaceURI();
            if (isNullOrEmpty(prefix))
            {
                Node xmlnsAttribute =
                        parentNode.getAttributes().getNamedItem("xmlns");
                if (xmlnsAttribute != null)
                    return ((Attr) xmlnsAttribute).getValue();
            }
            else if (isEquals(prefix, parentPrefix))
            {
                if (!isNullOrEmpty(parentNamespaceUri))
                    return normalizeNamespace(parentNamespaceUri);
            }
        }
        if ("xml".equals(prefix))
            return normalizeNamespace(XML_NS_URI);
        return null;
    }

    /**
     * Normalizes the namespace.
     *
     * @param namespace the namespace to normalize.
     * @return normalized namespace.
     */
    private static String normalizeNamespace(String namespace)
    {
        if (namespace.endsWith("/"))
        {
            return namespace.substring(0, namespace.length() - 1);
        }
        return namespace;
    }

    /**
     * Indicates whether element has any child element.
     *
     * @param element the namespace to analyze.
     * @return true if element has any child element otherwise false.
     */
    public static boolean hasChildElements(Element element)
    {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++)
        {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates W3C Document.
     *
     * @return the W3C Document.
     * @throws ParserConfigurationException if there is some error during operation.
     */
    public static Document createDocument()
            throws ParserConfigurationException
    {
        return getDocumentBuilderFactory().newDocumentBuilder().newDocument();
    }

    /**
     * Creates W3C Document from the xml.
     *
     * @param xml the xml that needs to be converted.
     * @return the W3C Document.
     * @throws Exception is there is some error during operation.
     */
    public static Document createDocument(String xml)
            throws Exception
    {
        DocumentBuilder documentBuilder = getDocumentBuilderFactory().newDocumentBuilder();
        if (!isNullOrEmpty(xml))
        {
            InputStream input = fromString(xml);
            return documentBuilder.parse(input);
        }
        else
        {
            return documentBuilder.newDocument();
        }
    }

    /**
     * Creates XML from W3C Document from the xml.
     *
     * @param document the xml that needs to be converted.
     * @return the XML.
     * @throws Exception is there is some error during operation.
     */
    public static String createXml(Document document)
            throws Exception
    {
        TransformerFactory transformerFactory =
                TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter stringWriter = new StringWriter();
        StreamResult result = new StreamResult(stringWriter);
        DOMSource source = new DOMSource(document);
        transformer.transform(source, result);
        return stringWriter.toString();
    }

    /**
     * Takes the given XML node (which may be a document) and recursively
     * strips it off whitespace-only text nodes, such as the indentation and
     * newlines added by our pretty-printing in
     * {@link XMLUtils#indentedWriteXML(Document, OutputStream)}.
     * @param node the XML node
     */
    public static void cleanupWhitespace(Node node)
    {
        NodeList children = node.getChildNodes();
        int length = children.getLength();

        for (int i = 0; i < length; i++)
        {
            Node childNode = children.item(i);
            // If this text node is an "only child", we don't want to trim it
            // because that whitespace is part of the
            // value (e.g. "<child>   hello    </child>"), hence the
            // `length > 1` check.
            if (childNode.getNodeType() == Node.TEXT_NODE && length > 1)
            {
                childNode.setTextContent(childNode.getTextContent().trim());
            }

            // Cleanup our children too
            cleanupWhitespace(childNode);
        }
    }

    public static DocumentBuilderFactory getDocumentBuilderFactory() throws ParserConfigurationException
    {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        // This is the PRIMARY defense against XML eXternal Entity injection (XXE).
        // If DTDs (doctypes) are disallowed, almost all XML entity attacks are prevented.
        builderFactory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        builderFactory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        builderFactory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);

        // As stated in the Java documentation
        // (see https://docs.oracle.com/en/java/javase/11/security/java-api-xml-processing-jaxp-security-guide.html#GUID-88B04BE2-35EF-4F61-B4FA-57A0E9102342)
        // "Feature for Secure Processing (FSP)" is the central mechanism to help safeguard XML processing.
        // It instructs XML processors, such as parsers, validators, and transformers, to try and process XML securely.
        // Exists from JDK6.
        builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // Disable external DTDs as well
        builderFactory.setFeature(LOAD_EXTERNAL_DTD_FEATURE, false);

        // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
        // (see https://research.nccgroup.com/2014/05/19/xml-schema-dtd-and-entity-attacks-a-compendium-of-known-techniques/).
        builderFactory.setXIncludeAware(false);
        builderFactory.setExpandEntityReferences(false);

        builderFactory.setNamespaceAware(true);

        return builderFactory;
    }
}
