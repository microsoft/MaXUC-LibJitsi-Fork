// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class CustomAnnotations
{
    /**
     * Marker annotation for methods called from or field accessed from native
     * code, so that we can tell IntelliJ to consider these to be entry points
     * (see Settings > Editor > Inspections > Declaration Redundancy
     * > Unused Declaration) and thus avoid warnings about unused code without
     * the sledgehammer that is <tt>@SuppressWarnings("unused")</tt>.
     */
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.SOURCE)
    @Inherited
    public @interface CalledFromNativeCode {}

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
    public @interface NotNull {}

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Nullable {}

}
