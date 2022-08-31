/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "MacCoreaudio_util.h"

#include "device.h"

#include <string.h>

#define ATTACH() \
JNIEnv *env = NULL;  \
\
int envAttached = 0; \
int shouldDetach = 0; \
\
int getEnvRes = (*MacCoreaudio_VM)->GetEnv(MacCoreaudio_VM, (void**) &env, JNI_VERSION_1_6); \
\
if (getEnvRes == JNI_EDETACHED) \
{ \
    if((*MacCoreaudio_VM)->AttachCurrentThreadAsDaemon( \
        MacCoreaudio_VM, \
        (void**) &env, \
        NULL) \
    == 0) \
    { \
        envAttached = 1; \
        shouldDetach = 1; \
    } \
} \
else if (getEnvRes == JNI_OK) \
{ \
    envAttached = 1; \
} \

#define DETACH() \
if (shouldDetach == 1) \
{ \
	(*MacCoreaudio_VM)->DetachCurrentThread(MacCoreaudio_VM); \
} \


/**
 * JNI utilities.
 *
 * @author Vincent Lucas
 */

// Private static objects.

static JavaVM * MacCoreaudio_VM = NULL;

static jclass MacCoreaudio_devicesChangedCallbackClass = 0;
static jmethodID MacCoreaudio_devicesChangedCallbackMethodID = 0;

void MacCoreaudio_initHotplug(
        void);
void MacCoreaudio_freeHotplug(
        void);


// Implementation

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *pvt)
{
    MacCoreaudio_VM = vm;
    MacCoreaudio_log("MacCoreAudio_util: JNI loaded");
    MacCoreaudio_initHotplug();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *pvt)
{
    MacCoreaudio_log("MacCoreAudio_util: JNI unloading");
    MacCoreaudio_freeHotplug();
    MacCoreaudio_VM = NULL;
}

/**
 * Gets a new <tt>jbyteArray</tt> instance which is initialized with the bytes
 * of a specific C string i.e. <tt>const char *</tt>.
 *
 * @param env
 * @param str the bytes/C string to initialize the new <tt>jbyteArray</tt>
 * instance with
 * @return a new <tt>jbyteArray</tt> instance which is initialized with the
 * bytes of the specified <tt>str</tt>
 */
jbyteArray MacCoreaudio_getStrBytes(
        JNIEnv *env,
        const char *str)
{
    jbyteArray bytes;

    if (str)
    {
        size_t length = strlen(str);

        bytes = (*env)->NewByteArray(env, length);
        if (bytes && length)
            (*env)->SetByteArrayRegion(env, bytes, 0, length, (jbyte *) str);
    }
    else
        bytes = NULL;
    return bytes;
}

/**
 * Returns a callback method identifier.
 *
 * @param env
 * @param callback The object called back.
 * @param callbackFunctionName The name of the function used for the callback.
 *
 * @return A callback method identifier. 0 if the callback function is not
 * found.
 */
jmethodID MacCoreaudio_getCallbackMethodID(
        JNIEnv *env,
        jobject callback,
        char* callbackFunctionName)
{
    jclass callbackClass;
    jmethodID callbackMethodID = NULL;

    if(callback)
    {
        if((callbackClass = (*env)->GetObjectClass(env, callback)))
        {
            callbackMethodID = (*env)->GetMethodID(
                    env,
                    callbackClass,
                    callbackFunctionName,
                    "([BI)V");
            (*env)->DeleteLocalRef(env, callbackClass);
        }
    }

    return callbackMethodID;
}

/**
 * Calls back the java side when respectively reading / wrtiting the input
 * /output stream.
 */
void MacCoreaudio_callbackMethod(
        char *buffer,
        int bufferLength,
        void* callback,
        void* callbackMethod)
{    	
	ATTACH();

    if(!envAttached)
    {
		return;
	}
	
	jbyteArray bufferBytes = (*env)->NewByteArray(env, bufferLength);
	(*env)->SetByteArrayRegion(
			env,
			bufferBytes,
			0,
			bufferLength,
			(jbyte *) buffer);

	(*env)->CallVoidMethod(
			env,
			callback,
			(jmethodID) callbackMethod,
			bufferBytes,
			bufferLength);

	jbyte* bytes = (*env)->GetByteArrayElements(env, bufferBytes, NULL);
	memcpy(buffer, bytes, bufferLength);
	(*env)->ReleaseByteArrayElements(env, bufferBytes, bytes, 0);
	(*env)->DeleteLocalRef(env, bufferBytes);
	
	DETACH();
}

/**
 * Calls back the java side when the device list has changed.
 */
void MacCoreaudio_devicesChangedCallbackMethod(void)
{
    MacCoreaudio_log("MacCoreAudio_util_devicesChangedCallbackMethod: Notified that devices have changed");

    ATTACH();
    
    if(!envAttached)
    {
		return;
	}
	
	jclass class = MacCoreaudio_devicesChangedCallbackClass;
	jmethodID methodID = MacCoreaudio_devicesChangedCallbackMethodID;
	if(class && methodID)
	{
		(*env)->CallStaticVoidMethod(env, class, methodID);
	}
	
	DETACH();
}

/**
 * Initializes the hotplug callback process.
 */
void MacCoreaudio_initHotplug(
        void)
{
    MacCoreaudio_log("MacCoreAudio_util_initHotplug: Initializing device hotplug");
	ATTACH();

    if(envAttached)
    {

        if(MacCoreaudio_devicesChangedCallbackClass == NULL
                && MacCoreaudio_devicesChangedCallbackMethodID == NULL)
        {
            jclass devicesChangedCallbackClass = (*env)->FindClass(
                    env,
                    "org/jitsi/impl/neomedia/device/CoreAudioDevice");

            if (devicesChangedCallbackClass)
            {
                devicesChangedCallbackClass
                    = (*env)->NewGlobalRef(env, devicesChangedCallbackClass);

                if (devicesChangedCallbackClass)
                {
                    jmethodID devicesChangedCallbackMethodID
                        = (*env)->GetStaticMethodID(
                                env,
                                devicesChangedCallbackClass,
                                "devicesChangedCallback",
                                "()V");

                    if (devicesChangedCallbackMethodID)
                    { 
                        MacCoreaudio_devicesChangedCallbackClass
                            = devicesChangedCallbackClass;
                        MacCoreaudio_devicesChangedCallbackMethodID
                            = devicesChangedCallbackMethodID;

                        MacCoreaudio_initializeHotplug(
                                MacCoreaudio_devicesChangedCallbackMethod);
                    }
                }
            }
        }        
    }	
	DETACH();
}

/**
 * Frees the hotplug callback process.
 */
void MacCoreaudio_freeHotplug(
        void)
{
    MacCoreaudio_log("MacCoreAudio_util_freeHotplug: Freeing device hotplug callback process");
    MacCoreaudio_uninitializeHotplug();

	ATTACH();

    if(!envAttached)
    {
		goto EXIT_LABEL;
	}
	
	(*env)->DeleteGlobalRef(
			env,
			MacCoreaudio_devicesChangedCallbackClass);        
    
	DETACH();
	
EXIT_LABEL:
	MacCoreaudio_devicesChangedCallbackClass = NULL;
    MacCoreaudio_devicesChangedCallbackMethodID = NULL;
}

/**
 * Logs the corresponding error message.
 *
 * @param error_format The format of the error message.
 * @param ... The list of variable specified in the format argument.
 */
void MacCoreaudio_log(
        const char * error_format,
        ...)
{
	ATTACH();

    if(!envAttached)
    {
		return;
	}
		
	jclass clazz = (*env)->FindClass(
			env,
			"org/jitsi/impl/neomedia/device/CoreAudioDevice");
	if (clazz)
	{
		jmethodID methodID
			= (*env)->GetStaticMethodID(env, clazz, "log", "([B)V");

		int error_length = 2048;
		char error[error_length];
		va_list arg;
		va_start (arg, error_format);
		vsnprintf(error, error_length, error_format, arg);
		va_end (arg);

		int str_len = strlen(error);
		jbyteArray bufferBytes = (*env)->NewByteArray(env, str_len);
		(*env)->SetByteArrayRegion(
				env,
				bufferBytes,
				0,
				str_len,
				(jbyte *) error);

		(*env)->CallStaticVoidMethod(env, clazz, methodID, bufferBytes);
    }
	
	DETACH();
}

