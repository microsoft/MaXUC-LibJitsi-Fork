/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import ch.imvs.sdes4j.srtp.*;

/**
 * SDES based SRTP MediaStream encryption control.
 *
 * @author Ingo Bauersachs
 */
public interface SDesControl
    extends SrtpControl
{
    /**
     * Name of the config setting that supplies the default enabled cipher
     * suites. Cipher suites are comma-separated.
     */
    String SDES_CIPHER_SUITES =
        "net.java.sip.communicator.service.neomedia.SDES_CIPHER_SUITES";

    /**
     * Set the enabled SDES ciphers.
     *
     * @param ciphers The list of enabled ciphers.
     */
    void setEnabledCiphers(Iterable<String> ciphers);

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    SrtpCryptoAttribute[] getInitiatorCryptoAttributes();

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and creates the local crypto attribute. Used when the control
     * is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     *
     * @return The local crypto attribute for the answer of the offer or null if
     *         no matching cipher suite could be found.
     */
    SrtpCryptoAttribute responderSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);

    /**
     * Select the local crypto attribute from the initial offering (@see
     * {@link #getInitiatorCryptoAttributes()}) based on the peer's first
     * matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     *
     * @return A SrtpCryptoAttribute when a matching cipher suite was found.
     * Null otherwise.
     */
    SrtpCryptoAttribute initiatorSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);
}
