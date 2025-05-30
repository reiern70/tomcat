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
package org.apache.naming.factory;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceLinkRef;
import org.apache.naming.StringManager;

/**
 * <p>
 * Object factory for resource links.
 * </p>
 *
 * @author Remy Maucherat
 */
public class ResourceLinkFactory implements ObjectFactory {

    // ------------------------------------------------------- Static Variables

    protected static final StringManager sm = StringManager.getManager(ResourceLinkFactory.class);

    /**
     * Global naming context.
     */
    private static Context globalContext = null;

    private static final Map<ClassLoader,Map<String,String>> globalResourceRegistrations = new ConcurrentHashMap<>();

    // --------------------------------------------------------- Public Methods

    /**
     * Set the global context (note: can only be used once).
     *
     * @param newGlobalContext new global context value
     */
    public static void setGlobalContext(Context newGlobalContext) {
        globalContext = newGlobalContext;
    }


    public static void registerGlobalResourceAccess(Context globalContext, String localName, String globalName) {
        validateGlobalContext(globalContext);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        // Web application initialization is single threaded so this is
        // safe.
        globalResourceRegistrations.computeIfAbsent(cl, k -> new HashMap<>()).put(localName, globalName);
    }


    public static void deregisterGlobalResourceAccess(Context globalContext, String localName) {
        validateGlobalContext(globalContext);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Map<String,String> registrations = globalResourceRegistrations.get(cl);
        if (registrations != null) {
            registrations.remove(localName);
        }
    }


    public static void deregisterGlobalResourceAccess(Context globalContext) {
        validateGlobalContext(globalContext);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        globalResourceRegistrations.remove(cl);
    }


    private static void validateGlobalContext(Context globalContext) {
        if (ResourceLinkFactory.globalContext != null && ResourceLinkFactory.globalContext != globalContext) {
            throw new SecurityException(sm.getString("resourceLinkFactory.invalidGlobalContext"));
        }
    }


    private static boolean validateGlobalResourceAccess(String globalName) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            Map<String,String> registrations = globalResourceRegistrations.get(cl);
            if (registrations != null && registrations.containsValue(globalName)) {
                return true;
            }
            cl = cl.getParent();
        }
        return false;
    }


    // -------------------------------------------------- ObjectFactory Methods

    /**
     * Create a new resource instance.
     *
     * @param name        the bound name
     * @param nameCtx     unused
     * @param environment unused
     *
     * @return the object instance
     *
     * @throws NamingException if an error occur creating the instance
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
            throws NamingException {

        if (!(obj instanceof ResourceLinkRef ref)) {
            return null;
        }

        // Can we process this request?

        // Read the global ref addr
        String globalName;
        RefAddr refAddr = ref.get(ResourceLinkRef.GLOBALNAME);
        if (refAddr != null) {
            globalName = refAddr.getContent().toString();
            // Confirm that the current web application is currently configured
            // to access the specified global resource
            if (!validateGlobalResourceAccess(globalName)) {
                return null;
            }
            Object result = globalContext.lookup(globalName);
            // Check the expected type
            String expectedClassName = ref.getClassName();
            if (expectedClassName == null) {
                throw new IllegalArgumentException(sm.getString("resourceLinkFactory.nullType", name, globalName));
            }
            try {
                Class<?> expectedClazz =
                        Class.forName(expectedClassName, true, Thread.currentThread().getContextClassLoader());
                if (!expectedClazz.isAssignableFrom(result.getClass())) {
                    throw new IllegalArgumentException(sm.getString("resourceLinkFactory.wrongType", name, globalName,
                            expectedClassName, result.getClass().getName()));
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(
                        sm.getString("resourceLinkFactory.unknownType", name, globalName, expectedClassName), e);
            }
            return result;
        }

        return null;
    }
}
