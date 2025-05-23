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
package org.apache.catalina.mapper;

import org.apache.catalina.Wrapper;

/**
 * Encapsulates information used to register a Wrapper mapping.
 *
 * @param mapping      The URL pattern
 * @param wrapper      The wrapper for the Servlet
 * @param jspWildCard  Is this a mapping for JSP files?
 * @param resourceOnly Is this a resource only mapping?
 */
public record WrapperMappingInfo(String mapping, Wrapper wrapper, boolean jspWildCard, boolean resourceOnly) {

    public String getMapping() {
        return mapping;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public boolean isJspWildCard() {
        return jspWildCard;
    }

    public boolean isResourceOnly() {
        return resourceOnly;
    }

}
