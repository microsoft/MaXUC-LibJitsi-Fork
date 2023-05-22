/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
// Portions (c) Microsoft Corporation. All rights reserved.

/**
 * \file DSManager.cpp
 * \brief DirectShow capture devices manager.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 * \date 2010
 */

#include <cstdlib>

#include "DSManager.h"
#include "DSCaptureDevice.h"
#include "JavaLogger.h"
#include <qedit.h>
#include <stdio.h>

DSManager::DSManager(JavaLogger* logger)
{
    HRESULT hr = ::CoInitializeEx(NULL, COINIT_MULTITHREADED);

    if (SUCCEEDED(hr))
    {
        logger->debug("CoInitializeEx succeeded");
        initCaptureDevices(logger);
    }
    else
    {
        logger->error("CoInitializeEx failed: 0x%x", hr);
    }

    /*
     * Each successful call to CoInitializeEx must be balanced by a
     * corresponding call to CoUninitialize in order to close the COM library
     * gracefully on a thread. Unfortunately, the multithreaded architectures of
     * FMJ and libjitsi do not really guarantee that the destructor of this
     * DSManager will be invoked on the same thread on which the constructor of
     * this DSManager has been invoked in the first place.
     */
    _coUninitialize = false;
}

DSManager::~DSManager()
{
    for(std::list<DSCaptureDevice*>::iterator it = m_devices.begin() ; it != m_devices.end() ; ++it)
        delete *it;
    m_devices.clear();

    /*
     * Each successful call to CoInitializeEx must be balanced by a
     * corresponding call to CoUninitialize in order to close the COM library
     * gracefully on a thread.
     */
    if (_coUninitialize)
        ::CoUninitialize();
}

std::list<DSCaptureDevice*> DSManager::getDevices() const
{
    return m_devices;
}

void DSManager::initCaptureDevices(JavaLogger* logger)
{
    HRESULT ret = 0;
    VARIANT name;
    VARIANT path;
    ICreateDevEnum* devEnum = NULL;
    IEnumMoniker* monikerEnum = NULL;
    IMoniker* moniker = NULL;

    if(m_devices.size() > 0)
    {
        /* clean up our list in case of reinitialization */
        for(std::list<DSCaptureDevice*>::iterator it = m_devices.begin() ; it != m_devices.end() ; ++it)
            delete *it;
        m_devices.clear();
    }

    /* get the available devices list */
    ret
        = CoCreateInstance(
                CLSID_SystemDeviceEnum,
                NULL,
                CLSCTX_INPROC_SERVER,
                IID_ICreateDevEnum,
                (void**) &devEnum);
    if(FAILED(ret))
    {
        logger->error("CoCreateInstance failed: 0x%x", ret);
        return;
    }

    ret
        = devEnum->CreateClassEnumerator(
                CLSID_VideoInputDeviceCategory,
                &monikerEnum,
                0);
    /* error or no devices */
    if(FAILED(ret) || ret == S_FALSE)
    {
        logger->error("CreateClassEnumerator failed: 0x%x", ret);
        devEnum->Release();
        return;
    }

    /* loop and initialize all available capture devices */
    while(monikerEnum->Next(1, &moniker, 0) == S_OK)
    {
        DSCaptureDevice* captureDevice = NULL;
        IPropertyBag* propertyBag = NULL;

        {
          IBaseFilter* cp = NULL;
          HRESULT btoRes = moniker->BindToObject(0, 0, IID_IBaseFilter, (void**)&cp);
          if(FAILED(btoRes))
          {
            logger->debug("BindToObject Failed: 0x%x", btoRes);
          }
          else
          {
            logger->debug("BindToObject Succeeded");
            IAMVfwCaptureDialogs* vfw = NULL;
            HRESULT qiRes = cp->QueryInterface(IID_IAMVfwCaptureDialogs, (void**)&vfw);
            if(FAILED(qiRes))
            {
              logger->debug("QueryInterface Failed: 0x%x", qiRes);
            }
            else
            {
              logger->debug("QueryInterface Succeeded");

              if(vfw)
              {
                logger->debug("vfw was true");
                vfw->Release();
                cp->Release();
                continue;
              }
            }
          }
        }

        /* get properties of the device */
        ret = moniker->BindToStorage(0, 0, IID_IPropertyBag, (void**)&propertyBag);
        if(FAILED(ret))
        {
            logger->error("BindToStorage failed: 0x%x", ret);
        }
        else
        {
            logger->debug("BindToStorage succeeded");

            VariantInit(&name);
            ret = propertyBag->Read(L"FriendlyName", &name, 0);
            if(FAILED(ret))
            {
                logger->error("Failed to get friendly name for device: 0x%x", ret);
                VariantClear(&name);
                propertyBag->Release();
                moniker->Release();
                continue;
            }

            VariantInit(&path);
            ret = propertyBag->Read(L"DevicePath", &path, 0);
            if(FAILED(ret))
            {
                logger->error("Failed to get path for device: 0x%x", ret);
                VariantClear(&path);
                propertyBag->Release();
                moniker->Release();
                continue;
            }

            logger->debug("Found device with name: %S and path: %S\n", name.bstrVal, path.bstrVal);

            /*
             * Initialize a new DSCaptureDevice instance and add it to the list
             * of DSCaptureDevice instances.
             */
            captureDevice = new DSCaptureDevice(name.bstrVal, path.bstrVal);
            if(captureDevice)
            {
                HRESULT idRes = captureDevice->initDevice(moniker);

                if (SUCCEEDED(idRes))
                {
                    logger->debug("captureDevice created successfully");
                    m_devices.push_back(captureDevice);
                }
                else
                {
                    logger->error("Failed to initialize capture device: 0x%x", idRes);
                    delete captureDevice;
                }
            }
            else
            {
                logger->error("Failed to create DSCaptureDevice object");
            }

            /* clean up */
            VariantClear(&name);
            VariantClear(&path);
            propertyBag->Release();
        }
        moniker->Release();
    }

    /* cleanup */
    monikerEnum->Release();
    devEnum->Release();
}
