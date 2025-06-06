/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.runtime;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import jakarta.el.VariableMapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.BodyContent;
import jakarta.servlet.jsp.tagext.BodyTag;
import jakarta.servlet.jsp.tagext.Tag;

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;

/**
 * Bunch of util methods that are used by code generated for useBean, getProperty and setProperty.
 *
 * @author Mandar Raje
 * @author Shawn Bayern
 */
public class JspRuntimeLibrary {

    public static final boolean GRAAL;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = nativeImageClazz.getMethod("inImageCode").invoke(null) != null;
            // Note: This will also be true for the Graal substrate VM
        } catch (ClassNotFoundException e) {
            // Must be Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
        }
        GRAAL = result || System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Returns the value of the jakarta.servlet.error.exception request attribute value, if present, otherwise the value
     * of the jakarta.servlet.jsp.jspException request attribute value. This method is called at the beginning of the
     * generated servlet code for a JSP error page, when the "exception" implicit scripting language variable is
     * initialized.
     *
     * @param request The Servlet request
     *
     * @return the throwable in the error attribute if any
     */
    public static Throwable getThrowable(ServletRequest request) {
        Throwable error = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (error == null) {
            error = (Throwable) request.getAttribute(PageContext.EXCEPTION);
            if (error != null) {
                /*
                 * The only place that sets JSP_EXCEPTION is PageContextImpl.handlePageException(). It really should set
                 * SERVLET_EXCEPTION, but that would interfere with the ErrorReportValve. Therefore, if JSP_EXCEPTION is
                 * set, we need to set SERVLET_EXCEPTION.
                 */
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
            }
        }

        return error;
    }

    public static boolean coerceToBoolean(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        } else {
            return Boolean.parseBoolean(s);
        }
    }

    public static byte coerceToByte(String s) {
        if (s == null || s.isEmpty()) {
            return (byte) 0;
        } else {
            return Byte.parseByte(s);
        }
    }

    public static char coerceToChar(String s) {
        if (s == null || s.isEmpty()) {
            return (char) 0;
        } else {
            return s.charAt(0);
        }
    }

    public static double coerceToDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        } else {
            return Double.parseDouble(s);
        }
    }

    public static float coerceToFloat(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        } else {
            return Float.parseFloat(s);
        }
    }

    public static int coerceToInt(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        } else {
            return Integer.parseInt(s);
        }
    }

    public static short coerceToShort(String s) {
        if (s == null || s.isEmpty()) {
            return (short) 0;
        } else {
            return Short.parseShort(s);
        }
    }

    public static long coerceToLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        } else {
            return Long.parseLong(s);
        }
    }

    public static Object coerce(String s, Class<?> target) {

        boolean isNullOrEmpty = (s == null || s.isEmpty());

        if (target == Boolean.class) {
            if (isNullOrEmpty) {
                s = "false";
            }
            return Boolean.valueOf(s);
        } else if (target == Byte.class) {
            if (isNullOrEmpty) {
                return Byte.valueOf((byte) 0);
            } else {
                return Byte.valueOf(s);
            }
        } else if (target == Character.class) {
            if (isNullOrEmpty) {
                return Character.valueOf((char) 0);
            } else {
                @SuppressWarnings("null")
                Character result = Character.valueOf(s.charAt(0));
                return result;
            }
        } else if (target == Double.class) {
            if (isNullOrEmpty) {
                return Double.valueOf(0);
            } else {
                return Double.valueOf(s);
            }
        } else if (target == Float.class) {
            if (isNullOrEmpty) {
                return Float.valueOf(0);
            } else {
                return Float.valueOf(s);
            }
        } else if (target == Integer.class) {
            if (isNullOrEmpty) {
                return Integer.valueOf(0);
            } else {
                return Integer.valueOf(s);
            }
        } else if (target == Short.class) {
            if (isNullOrEmpty) {
                return Short.valueOf((short) 0);
            } else {
                return Short.valueOf(s);
            }
        } else if (target == Long.class) {
            if (isNullOrEmpty) {
                return Long.valueOf(0);
            } else {
                return Long.valueOf(s);
            }
        } else {
            return null;
        }
    }


    public static Object convert(String propertyName, String s, Class<?> t, Class<?> propertyEditorClass)
            throws JasperException {
        try {
            if (s == null) {
                if (t.equals(Boolean.class) || t.equals(Boolean.TYPE)) {
                    s = "false";
                } else {
                    return null;
                }
            }
            if (propertyEditorClass != null) {
                return getValueFromBeanInfoPropertyEditor(t, propertyName, s, propertyEditorClass);
            } else if (t.equals(Boolean.class) || t.equals(Boolean.TYPE)) {
                return Boolean.valueOf(s);
            } else if (t.equals(Byte.class) || t.equals(Byte.TYPE)) {
                if (s.isEmpty()) {
                    return Byte.valueOf((byte) 0);
                } else {
                    return Byte.valueOf(s);
                }
            } else if (t.equals(Character.class) || t.equals(Character.TYPE)) {
                if (s.isEmpty()) {
                    return Character.valueOf((char) 0);
                } else {
                    return Character.valueOf(s.charAt(0));
                }
            } else if (t.equals(Double.class) || t.equals(Double.TYPE)) {
                if (s.isEmpty()) {
                    return Double.valueOf(0);
                } else {
                    return Double.valueOf(s);
                }
            } else if (t.equals(Integer.class) || t.equals(Integer.TYPE)) {
                if (s.isEmpty()) {
                    return Integer.valueOf(0);
                } else {
                    return Integer.valueOf(s);
                }
            } else if (t.equals(Float.class) || t.equals(Float.TYPE)) {
                if (s.isEmpty()) {
                    return Float.valueOf(0);
                } else {
                    return Float.valueOf(s);
                }
            } else if (t.equals(Long.class) || t.equals(Long.TYPE)) {
                if (s.isEmpty()) {
                    return Long.valueOf(0);
                } else {
                    return Long.valueOf(s);
                }
            } else if (t.equals(Short.class) || t.equals(Short.TYPE)) {
                if (s.isEmpty()) {
                    return Short.valueOf((short) 0);
                } else {
                    return Short.valueOf(s);
                }
            } else if (t.equals(String.class)) {
                return s;
            } else if (t.getName().equals("java.lang.Object")) {
                return new String(s);
            } else {
                return getValueFromPropertyEditorManager(t, propertyName, s);
            }
        } catch (Exception ex) {
            throw new JasperException(ex);
        }
    }


    public static void introspect(Object bean, ServletRequest request) throws JasperException {
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            String value = request.getParameter(name);
            introspecthelper(bean, name, value, request, name, true);
        }
    }


    public static void introspecthelper(Object bean, String prop, String value, ServletRequest request, String param,
            boolean ignoreMethodNF) throws JasperException {
        Method method = null;
        Class<?> type = null;
        Class<?> propertyEditorClass = null;
        try {
            if (GRAAL) {
                method = getWriteMethod(bean.getClass(), prop);
                if (method.getParameterTypes().length > 0) {
                    type = method.getParameterTypes()[0];
                }
            } else {
                java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(bean.getClass());
                if (info != null) {
                    java.beans.PropertyDescriptor[] pd = info.getPropertyDescriptors();
                    for (java.beans.PropertyDescriptor propertyDescriptor : pd) {
                        if (propertyDescriptor.getName().equals(prop)) {
                            method = propertyDescriptor.getWriteMethod();
                            type = propertyDescriptor.getPropertyType();
                            propertyEditorClass = propertyDescriptor.getPropertyEditorClass();
                            break;
                        }
                    }
                }
            }
            if (method != null && type != null) {
                if (type.isArray()) {
                    if (request == null) {
                        throw new JasperException(Localizer.getMessage("jsp.error.beans.setproperty.noindexset"));
                    }
                    Class<?> t = type.getComponentType();
                    String[] values = request.getParameterValues(param);
                    // XXX Please check.
                    if (values == null) {
                        return;
                    }
                    if (t.equals(String.class)) {
                        method.invoke(bean, new Object[] { values });
                    } else {
                        createTypedArray(prop, bean, method, values, t, propertyEditorClass);
                    }
                } else {
                    if (value == null || (param != null && value.isEmpty())) {
                        return;
                    }
                    Object oval = convert(prop, value, type, propertyEditorClass);
                    if (oval != null) {
                        method.invoke(bean, oval);
                    }
                }
            }
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
        if (!ignoreMethodNF && (method == null)) {
            if (type == null) {
                throw new JasperException(
                        Localizer.getMessage("jsp.error.beans.noproperty", prop, bean.getClass().getName()));
            } else {
                throw new JasperException(Localizer.getMessage("jsp.error.beans.nomethod.setproperty", prop,
                        type.getName(), bean.getClass().getName()));
            }
        }
    }


    // -------------------------------------------------------------------
    // functions to convert builtin Java data types to string.
    // -------------------------------------------------------------------
    public static String toString(Object o) {
        return String.valueOf(o);
    }

    public static String toString(byte b) {
        return Byte.toString(b);
    }

    public static String toString(boolean b) {
        return Boolean.toString(b);
    }

    public static String toString(short s) {
        return Short.toString(s);
    }

    public static String toString(int i) {
        return Integer.toString(i);
    }

    public static String toString(float f) {
        return Float.toString(f);
    }

    public static String toString(long l) {
        return Long.toString(l);
    }

    public static String toString(double d) {
        return Double.toString(d);
    }

    public static String toString(char c) {
        return Character.toString(c);
    }


    /**
     * Create a typed array. This is a special case where params are passed through the request and the property is
     * indexed.
     *
     * @param propertyName        The property name
     * @param bean                The bean
     * @param method              The method
     * @param values              Array values
     * @param t                   The class
     * @param propertyEditorClass The editor for the property
     *
     * @throws JasperException An error occurred
     */
    public static void createTypedArray(String propertyName, Object bean, Method method, String[] values, Class<?> t,
            Class<?> propertyEditorClass) throws JasperException {

        try {
            if (propertyEditorClass != null) {
                Object[] tmpval = new Integer[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = getValueFromBeanInfoPropertyEditor(t, propertyName, values[i], propertyEditorClass);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Integer.class)) {
                Integer[] tmpval = new Integer[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Integer.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Byte.class)) {
                Byte[] tmpval = new Byte[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Byte.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Boolean.class)) {
                Boolean[] tmpval = new Boolean[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Boolean.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Short.class)) {
                Short[] tmpval = new Short[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Short.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Long.class)) {
                Long[] tmpval = new Long[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Long.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Double.class)) {
                Double[] tmpval = new Double[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Double.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Float.class)) {
                Float[] tmpval = new Float[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Float.valueOf(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(Character.class)) {
                Character[] tmpval = new Character[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Character.valueOf(values[i].charAt(0));
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(int.class)) {
                int[] tmpval = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Integer.parseInt(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(byte.class)) {
                byte[] tmpval = new byte[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Byte.parseByte(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(boolean.class)) {
                boolean[] tmpval = new boolean[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Boolean.parseBoolean(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(short.class)) {
                short[] tmpval = new short[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Short.parseShort(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(long.class)) {
                long[] tmpval = new long[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Long.parseLong(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(double.class)) {
                double[] tmpval = new double[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Double.parseDouble(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(float.class)) {
                float[] tmpval = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = Float.parseFloat(values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else if (t.equals(char.class)) {
                char[] tmpval = new char[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = values[i].charAt(0);
                }
                method.invoke(bean, new Object[] { tmpval });
            } else {
                Object[] tmpval = new Integer[values.length];
                for (int i = 0; i < values.length; i++) {
                    tmpval[i] = getValueFromPropertyEditorManager(t, propertyName, values[i]);
                }
                method.invoke(bean, new Object[] { tmpval });
            }
        } catch (RuntimeException | ReflectiveOperationException ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException("error in invoking method", ex);
        }
    }

    /**
     * Escape special shell characters.
     *
     * @param unescString The string to shell-escape
     *
     * @return The escaped shell string.
     */
    public static String escapeQueryString(String unescString) {
        if (unescString == null) {
            return null;
        }

        StringBuilder escStringBuilder = new StringBuilder();
        String shellSpChars = "&;`'\"|*?~<>^()[]{}$\\\n";

        for (int index = 0; index < unescString.length(); index++) {
            char nextChar = unescString.charAt(index);

            if (shellSpChars.indexOf(nextChar) != -1) {
                escStringBuilder.append('\\');
            }

            escStringBuilder.append(nextChar);
        }
        return escStringBuilder.toString();
    }


    public static Object handleGetProperty(Object o, String prop) throws JasperException {
        if (o == null) {
            throw new JasperException(Localizer.getMessage("jsp.error.beans.nullbean"));
        }
        Object value;
        try {
            Method method = getReadMethod(o.getClass(), prop);
            value = method.invoke(o, (Object[]) null);
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
        return value;
    }

    // handles <jsp:setProperty> with EL expression for 'value' attribute
    public static void handleSetPropertyExpression(Object bean, String prop, String expression, PageContext pageContext,
            ProtectedFunctionMapper functionMapper) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, PageContextImpl.proprietaryEvaluate(expression,
                    method.getParameterTypes()[0], pageContext, functionMapper));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, Object value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, value);
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, int value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Integer.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, short value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Short.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, long value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Long.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, double value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Double.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, float value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Float.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, char value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Character.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, byte value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Byte.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop, boolean value) throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, Boolean.valueOf(value));
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    /**
     * Reverse of Introspector.decapitalize.
     *
     * @param name The name
     *
     * @return the capitalized string
     */
    public static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    public static Method getWriteMethod(Class<?> beanClass, String prop) throws JasperException {
        Method result = null;
        Class<?> type = null;
        if (GRAAL) {
            String setter = "set" + capitalize(prop);
            Method[] methods = beanClass.getMethods();
            for (Method method : methods) {
                if (setter.equals(method.getName())) {
                    return method;
                }
            }
        } else {
            try {
                java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(beanClass);
                java.beans.PropertyDescriptor[] pd = info.getPropertyDescriptors();
                for (java.beans.PropertyDescriptor propertyDescriptor : pd) {
                    if (propertyDescriptor.getName().equals(prop)) {
                        result = propertyDescriptor.getWriteMethod();
                        type = propertyDescriptor.getPropertyType();
                        break;
                    }
                }
            } catch (Exception ex) {
                throw new JasperException(ex);
            }
        }
        if (result == null) {
            if (type == null) {
                throw new JasperException(
                        Localizer.getMessage("jsp.error.beans.noproperty", prop, beanClass.getName()));
            } else {
                throw new JasperException(Localizer.getMessage("jsp.error.beans.nomethod.setproperty", prop,
                        type.getName(), beanClass.getName()));
            }
        }
        return result;
    }

    public static Method getReadMethod(Class<?> beanClass, String prop) throws JasperException {
        Method result = null;
        Class<?> type = null;
        if (GRAAL) {
            String setter = "get" + capitalize(prop);
            Method[] methods = beanClass.getMethods();
            for (Method method : methods) {
                if (setter.equals(method.getName())) {
                    return method;
                }
            }
        } else {
            try {
                java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(beanClass);
                java.beans.PropertyDescriptor[] pd = info.getPropertyDescriptors();
                for (java.beans.PropertyDescriptor propertyDescriptor : pd) {
                    if (propertyDescriptor.getName().equals(prop)) {
                        result = propertyDescriptor.getReadMethod();
                        type = propertyDescriptor.getPropertyType();
                        break;
                    }
                }
            } catch (Exception ex) {
                throw new JasperException(ex);
            }
        }
        if (result == null) {
            if (type == null) {
                throw new JasperException(
                        Localizer.getMessage("jsp.error.beans.noproperty", prop, beanClass.getName()));
            } else {
                throw new JasperException(Localizer.getMessage("jsp.error.beans.nomethod", prop, beanClass.getName()));
            }
        }
        return result;
    }

    // *********************************************************************
    // PropertyEditor Support

    public static Object getValueFromBeanInfoPropertyEditor(Class<?> attrClass, String attrName, String attrValue,
            Class<?> propertyEditorClass) throws JasperException {
        try {
            PropertyEditor pe = (PropertyEditor) propertyEditorClass.getConstructor().newInstance();
            pe.setAsText(attrValue);
            return pe.getValue();
        } catch (Exception ex) {
            if (attrValue.isEmpty()) {
                return null;
            } else {
                throw new JasperException(Localizer.getMessage("jsp.error.beans.property.conversion", attrValue,
                        attrClass.getName(), attrName, ex.getMessage()));
            }
        }
    }

    public static Object getValueFromPropertyEditorManager(Class<?> attrClass, String attrName, String attrValue)
            throws JasperException {
        try {
            PropertyEditor propEditor = PropertyEditorManager.findEditor(attrClass);
            if (propEditor != null) {
                propEditor.setAsText(attrValue);
                return propEditor.getValue();
            } else if (attrValue.isEmpty()) {
                return null;
            } else {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.beans.propertyeditor.notregistered"));
            }
        } catch (IllegalArgumentException ex) {
            if (attrValue.isEmpty()) {
                return null;
            } else {
                throw new JasperException(Localizer.getMessage("jsp.error.beans.property.conversion", attrValue,
                        attrClass.getName(), attrName, ex.getMessage()));
            }
        }
    }


    // ************************************************************************
    // General Purpose Runtime Methods
    // ************************************************************************


    /**
     * Convert a possibly relative resource path into a context-relative resource path that starts with a '/'.
     *
     * @param request      The servlet request we are processing
     * @param relativePath The possibly relative resource path
     *
     * @return an absolute path
     */
    public static String getContextRelativePath(ServletRequest request, String relativePath) {

        if (relativePath.startsWith("/")) {
            return relativePath;
        }
        if (!(request instanceof HttpServletRequest)) {
            return relativePath;
        }
        HttpServletRequest hrequest = (HttpServletRequest) request;
        String uri = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (uri != null) {
            String pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (pathInfo == null) {
                if (uri.lastIndexOf('/') >= 0) {
                    uri = uri.substring(0, uri.lastIndexOf('/'));
                }
            }
        } else {
            uri = hrequest.getServletPath();
            if (uri.lastIndexOf('/') >= 0) {
                uri = uri.substring(0, uri.lastIndexOf('/'));
            }
        }
        return uri + '/' + relativePath;

    }


    /**
     * Perform a RequestDispatcher.include() operation, with optional flushing of the response beforehand.
     *
     * @param request      The servlet request we are processing
     * @param response     The servlet response we are processing
     * @param relativePath The relative path of the resource to be included
     * @param out          The Writer to whom we are currently writing
     * @param flush        Should we flush before the include is processed?
     *
     * @exception IOException      if thrown by the included servlet
     * @exception ServletException if thrown by the included servlet
     */
    public static void include(ServletRequest request, ServletResponse response, String relativePath, JspWriter out,
            boolean flush) throws IOException, ServletException {

        if (flush && !(out instanceof BodyContent)) {
            out.flush();
        }

        // FIXME - It is tempting to use request.getRequestDispatcher() to
        // resolve a relative path directly, but Catalina currently does not
        // take into account whether the caller is inside a RequestDispatcher
        // include or not. Whether Catalina *should* take that into account
        // is a spec issue currently under review. In the mean time,
        // replicate Jasper's previous behavior

        String resourcePath = getContextRelativePath(request, relativePath);
        RequestDispatcher rd = request.getRequestDispatcher(resourcePath);
        if (rd != null) {
            rd.include(request, new ServletResponseWrapperInclude(response, out));
        } else {
            throw new JasperException(Localizer.getMessage("jsp.error.include.exception", resourcePath));
        }

    }

    /**
     * URL encodes a string, based on the supplied character encoding.
     *
     * @param s   The String to be URL encoded.
     * @param enc The character encoding
     *
     * @return The URL encoded String
     */
    public static String URLEncode(String s, String enc) {
        if (s == null) {
            return "null";
        }
        if (enc == null) {
            enc = "ISO-8859-1"; // The default request encoding
        }
        Charset cs;
        try {
            cs = Charset.forName(enc);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            cs = StandardCharsets.ISO_8859_1;
        }
        return URLEncoder.encode(s, cs);
    }


    public static JspWriter startBufferedBody(PageContext pageContext, BodyTag tag) throws JspException {
        BodyContent out = pageContext.pushBody();
        tag.setBodyContent(out);
        tag.doInitBody();
        return out;
    }


    public static void releaseTag(Tag tag, InstanceManager instanceManager) {
        try {
            tag.release();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            Log log = LogFactory.getLog(JspRuntimeLibrary.class);
            log.warn(Localizer.getMessage("jsp.warning.tagRelease", tag.getClass().getName()), t);
        }
        try {
            instanceManager.destroyInstance(tag);
        } catch (Exception e) {
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            Log log = LogFactory.getLog(JspRuntimeLibrary.class);
            log.warn(Localizer.getMessage("jsp.warning.tagPreDestroy", tag.getClass().getName()), t);
        }

    }

    /**
     * This method parallels the logic of {@code SetSupport.doEndTag()}.
     *
     * @param pageContext pageContext
     * @param var name of the variable
     * @param value value to store
     * @param scope scope
     */
    public static void nonstandardSetTag(jakarta.servlet.jsp.PageContext pageContext, String var, Object value,
            int scope) {
        if (value == null) {
            // matches SetTag and removes the key from the specified scope
            pageContext.removeAttribute(var, scope);
        } else {
            if (scope == PageContext.PAGE_SCOPE) {
                // matches SetTag and cleans up the VariableMapper
                VariableMapper vm = pageContext.getELContext().getVariableMapper();
                if (vm != null) {
                    vm.setVariable(var, null);
                }
            }
            // does the all-important set of the correct scope
            pageContext.setAttribute(var, value, scope);
        }
    }
}
