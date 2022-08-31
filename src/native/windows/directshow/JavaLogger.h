// Copyright (c) Microsoft Corporation. All rights reserved.
#include <jni.h>

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_JAVA_LOGGER_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_JAVA_LOGGER_H_

#include <stdio.h>
#include <stdarg.h>

/**
* JavaLogger
*
* Wraps a net.java.sip.communicator.util.Logger
**/
class JavaLogger
{
  private:
    JNIEnv *env;
    jobject logger;

    void log(const char* level, const char* message)
    {
        jclass cls = env->GetObjectClass(logger);
        if (cls == 0)
        {
          fprintf(stderr, "Failed to find class for JavaLogger \n");
          fprintf(stderr, message);
          return;
        }
        jmethodID mid = env->GetMethodID(
          cls, level, "(Ljava/lang/Object;)V");
        if (mid == 0)
        {
          fprintf(stderr, "Failed to find method for JavaLogger with level: %s \n", level);
          fprintf(stderr, message);
          return;
        }
        jstring log = env->NewStringUTF(message);
        env->CallVoidMethod(logger, mid, log);
        env->DeleteLocalRef(log);
    }

  public:
    JavaLogger(JNIEnv *jniEnv, jclass cls) : env(jniEnv)
    {
        jfieldID fid = env->GetStaticFieldID(cls, "sLog", "Lorg/jitsi/util/Logger;");
        logger = env->GetStaticObjectField(cls, fid);
    }

    void debug(const char* message, ...)
    {
        char buffer[255];
        va_list args;
        va_start (args, message);
        vsnprintf (buffer, 255, message, args);
        va_end (args);
        log("debug", buffer);
    }

    void trace(const char* message, ...)
    {
        char buffer[255];
        va_list args;
        va_start (args, message);
        vsnprintf (buffer, 255, message, args);
        va_end (args);
        log("trace", buffer);
    }

    void info(const char* message, ...)
    {
        char buffer[255];
        va_list args;
        va_start (args, message);
        vsnprintf (buffer, 255, message, args);
        va_end (args);
        log("info", buffer);
    }

    void warn(const char* message, ...)
    {
        char buffer[255];
        va_list args;
        va_start (args, message);
        vsnprintf (buffer, 255, message, args);
        va_end (args);
        log("warn", buffer);
    }

    void error(const char* message, ...)
    {
        char buffer[255];
        va_list args;
        va_start (args, message);
        vsnprintf (buffer, 255, message, args);
        va_end (args);
        log("error", buffer);
    }

};

#endif
 