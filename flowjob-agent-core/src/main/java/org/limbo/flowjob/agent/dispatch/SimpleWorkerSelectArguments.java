/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.limbo.flowjob.agent.dispatch;

import org.apache.commons.lang3.StringUtils;
import org.limbo.flowjob.common.utils.attribute.Attributes;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Brozen
 * @since 2023-02-01
 */
public class SimpleWorkerSelectArguments implements WorkerSelectArgument {

    private String executorName;

    private DispatchOption dispatchOption;

    private Map<String, String> lbParameters;

    private static final String LB_PREFIX = "worker.lb.";

    public SimpleWorkerSelectArguments(String executorName, DispatchOption dispatchOption, Attributes attributes) {
        this.executorName = executorName;
        this.dispatchOption = dispatchOption;
        this.lbParameters = new HashMap<>();
        putStringEntry(this.lbParameters, attributes);
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public String getExecutorName() {
        return executorName;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public DispatchOption getDispatchOption() {
        return dispatchOption;
    }


    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public Map<String, String> getLBParameters() {
        return lbParameters;
    }


    /**
     * @param attrMap
     * @param attr
     */
    private void putStringEntry(Map<String, String> attrMap, Attributes attr) {
        if (attr == null) {
            return;
        }
        attr.toMap().entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null)
                .filter(entry -> entry.getKey().startsWith(LB_PREFIX))
                .filter(entry -> entry.getValue() instanceof String)
                .forEach(entry -> {
                    String value = (String) entry.getValue();
                    attrMap.put(entry.getKey(), value.replace(LB_PREFIX, ""));
                });
    }
}
