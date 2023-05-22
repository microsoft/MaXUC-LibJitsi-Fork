/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.transform.sdes;

import java.security.SecureRandom;
import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.protocol.event.CallPeerSecurityStatusEvent;
import org.jitsi.util.*;

import ch.imvs.sdes4j.srtp.*;

/**
 * Default implementation of {@link SDesControl} that supports the crypto suites
 * of the original RFC4568 and the KDR parameter, but nothing else.
 *
 * @author Ingo Bauersachs
 */
public class SDesControlImpl
    implements SDesControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>SDesControlImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(SDesControlImpl.class);

    /**
     * List of enabled crypto suites.
     */
    private List<String> enabledCryptoSuites = new ArrayList<String>(3)
    {
        private static final long serialVersionUID = 0L;

        {
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80);
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32);
            add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80);
        }
    };

    private SrtpSDesFactory sdesFactory;
    private SrtpCryptoAttribute[] attributes;
    private SDesTransformEngine engine;
    private SrtpCryptoAttribute selectedInAttribute;
    private SrtpCryptoAttribute selectedOutAttribute;
    private SrtpListener srtpListener;

    /**
     * SDESControl
     */
    public SDesControlImpl()
    {
        sdesFactory = new SrtpSDesFactory();
        Random r = new SecureRandom();
        sdesFactory.setRandomGenerator(r);
    }

    @Override
    public void setEnabledCiphers(Iterable<String> ciphers)
    {
        enabledCryptoSuites.clear();
        for(String c : ciphers)
            enabledCryptoSuites.add(c);
    }

    @Override
    public void cleanup()
    {
        if (engine != null)
        {
            engine.close();
            engine = null;
        }
    }

    @Override
    public void setSrtpListener(SrtpListener srtpListener)
    {
        this.srtpListener = srtpListener;
    }

    @Override
    public SrtpListener getSrtpListener()
    {
        return srtpListener;
    }

    @Override
    public boolean getSecureCommunicationStatus()
    {
        return engine != null;
    }

    /**
     * Not used.
     * @param masterSession not used.
     */
    @Override
    public void setMasterSession(boolean masterSession)
    {}

    @Override
    public void start(MediaType type)
    {
        // in srtp the started and security event is one after another
        // in some other security mechanisms (e.g. zrtp) there can be started
        // and no security one or security timeout event
        srtpListener.securityNegotiationStarted(
            type.equals(MediaType.AUDIO) ?
                CallPeerSecurityStatusEvent.AUDIO_SESSION
                : CallPeerSecurityStatusEvent.VIDEO_SESSION,
                this);

        srtpListener.securityTurnedOn(
            type.equals(MediaType.AUDIO) ?
                CallPeerSecurityStatusEvent.AUDIO_SESSION
                : CallPeerSecurityStatusEvent.VIDEO_SESSION,
            selectedInAttribute.getCryptoSuite().encode(), this);
    }

    @Override
    public void setMultistream(SrtpControl master)
    {
    }

    @Override
    public TransformEngine getTransformEngine()
    {
        if(engine == null)
        {
            engine = new SDesTransformEngine(selectedInAttribute,
                    selectedOutAttribute);
        }
        return engine;
    }

    /**
     * Initializes the available SRTP crypto attributes containing: the
     * crypto-suite, the key-param and the session-param.
     */
    private void initAttributes()
    {
        if(attributes == null)
        {
            attributes = new SrtpCryptoAttribute[enabledCryptoSuites.size()];
            for (int i = 0; i < attributes.length; i++)
            {
                attributes[i] = sdesFactory.createCryptoAttribute(
                        i + 1,
                        enabledCryptoSuites.get(i));
            }
        }
    }

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    @Override
    public SrtpCryptoAttribute[] getInitiatorCryptoAttributes()
    {
        initAttributes();

        // While acting as initiator we may actually be responding to a SIP
        // ReINVITE not containing SDP.  In that case we should continue to use
        // the existing attribute.
        if (selectedOutAttribute != null)
        {
            SrtpCryptoAttribute[] attr = {selectedOutAttribute};
            return attr;
        }

        return attributes;
    }

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and uses the corresponding local crypto attribute. Used when
     * the control is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     *
     * @return The local crypto attribute for the answer of the offer or null if
     *         no matching cipher suite could be found.
     */
    @Override
    public SrtpCryptoAttribute responderSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes)
    {
        initAttributes();

        for (SrtpCryptoAttribute ea : peerAttributes)
        {
            for (int ii = 0; ii < enabledCryptoSuites.size(); ii++)
            {
                String suite = enabledCryptoSuites.get(ii);
                if (suite.equals(ea.getCryptoSuite().encode()))
                {
                    selectedInAttribute = ea;

                    // No need to update the output attribute if we have previously
                    // created one that matches the input attribute.
                    if ((selectedOutAttribute == null) ||
                        (selectedOutAttribute.getTag() != selectedInAttribute.getTag()) ||
                        (!selectedOutAttribute.getCryptoSuite().encode()
                            .equals(selectedInAttribute.getCryptoSuite().encode())))
                    {
                        if (selectedOutAttribute != null)
                        {
                            logger.error("Crypto paramters changed mid call");
                        }

                        // Use the tag from the offer, as per RFC4568 5.1.2.
                        selectedOutAttribute = sdesFactory.createCryptoAttribute(
                            selectedInAttribute.getTag(), suite);
                    }

                    return selectedOutAttribute;
                }
            }
        }
        return null;
    }

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
    @Override
    public SrtpCryptoAttribute initiatorSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes)
    {
        SrtpCryptoAttribute initAttr = null;

        for (SrtpCryptoAttribute peerCA : peerAttributes)
        {
            SrtpCryptoAttribute localAttr = null;

            // While we are acting as initiator we may actually be dealing with
            // an ACK from the far end and so already have a local attribute.
            // If that still works then there's no reason to change.
            if ((selectedOutAttribute != null) &&
                (selectedOutAttribute.getCryptoSuite().encode().equals(
                                            peerCA.getCryptoSuite().encode())))
            {
                localAttr = selectedOutAttribute;
            }
            else
            {
                for (SrtpCryptoAttribute localCA : attributes)
                {
                    if (localCA.getCryptoSuite().equals(
                                                      peerCA.getCryptoSuite()))
                    {
                        localAttr = localCA;
                    }
                }
            }

            if (localAttr != null)
            {
                if ((selectedOutAttribute != null) &&
                    (localAttr != selectedOutAttribute))
                {
                    logger.error("Crypto paramters changed mid call");
                }

                selectedInAttribute = peerCA;
                selectedOutAttribute = localAttr;

                initAttr = selectedInAttribute;
                break;
            }
        }

        return initAttr;
    }

    @Override
    public void setConnector(AbstractRTPConnector newValue)
    {
    }

    /**
     * Returns true, SDES always requires the secure transport of its keys.
     *
     * @return true
     */
    @Override
    public boolean requiresSecureSignalingTransport()
    {
        return true;
    }
}
