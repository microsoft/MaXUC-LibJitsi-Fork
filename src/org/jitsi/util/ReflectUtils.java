// Copyright (c) Microsoft Corporation. All rights reserved.
package org.jitsi.util;

import java.lang.reflect.Field;

/**
 * A simple helper class which we use for Java reflection.
 */
public final class ReflectUtils
{

    /**
     * Prevents the initialization of <tt>ReflectUtils</tt> instances.
     */
    private ReflectUtils()
    {
    }

    /**
     * Get a private class field by using Java Reflection.
     *
     * @param object       object from which the field will be obtained
     * @param field        field name
     * @param isSuperClass <tt>true</tt> if the field is a superclass field
     * @param clazz        the class in which the field will be cast
     * @param <T>          the type of field
     * @return private class field
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public static <T> T getPrivateField(Object object,
                                        String field,
                                        boolean isSuperClass,
                                        Class<T> clazz) throws NoSuchFieldException, IllegalAccessException
    {
        Class<?> objectClass = isSuperClass ? object.getClass().getSuperclass(): object.getClass();
        Field pfield = objectClass.getDeclaredField(field);
        pfield.setAccessible(true);

        return clazz.cast(pfield.get(object));
    }
}
