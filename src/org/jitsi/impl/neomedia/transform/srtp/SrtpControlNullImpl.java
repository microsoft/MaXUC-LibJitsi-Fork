// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.impl.neomedia.transform.srtp;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;

public class SrtpControlNullImpl implements SrtpControl
{
    @Override
    public void cleanup()
    {
    }

    @Override
    public void setSrtpListener(SrtpListener srtpListener)
    {
    }

    @Override
    public SrtpListener getSrtpListener()
    {
        return null;
    }

    @Override
    public boolean getSecureCommunicationStatus()
    {
        return false;
    }

    @Override
    public void setMasterSession(boolean masterSession)
    {
    }

    @Override
    public void start(MediaType mediaType)
    {
    }

    @Override
    public void setMultistream(SrtpControl master)
    {
    }

    @Override
    public TransformEngine getTransformEngine()
    {
        return null;
    }

    @Override
    public void setConnector(AbstractRTPConnector connector)
    {
    }

    @Override
    public boolean requiresSecureSignalingTransport()
    {
        return false;
    }
}
