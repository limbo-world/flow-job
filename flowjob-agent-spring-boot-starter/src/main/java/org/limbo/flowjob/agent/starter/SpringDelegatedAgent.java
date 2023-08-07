/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limbo.flowjob.agent.starter;

import lombok.Setter;
import lombok.experimental.Delegate;
import org.limbo.flowjob.agent.ScheduleAgent;
import org.limbo.flowjob.agent.starter.properties.AgentProperties;
import org.springframework.beans.factory.DisposableBean;

import javax.inject.Inject;

/**
 * @author Brozen
 * @since 2022-09-11
 */
public class SpringDelegatedAgent implements ScheduleAgent, DisposableBean {

    @Delegate(types = ScheduleAgent.class)
    private final ScheduleAgent delegated;

    @Setter(onMethod_ = @Inject)
    private AgentProperties properties;


    public SpringDelegatedAgent(ScheduleAgent delegated) {
        this.delegated = delegated;
    }


    /**
     * Bean 销毁时，停止 Worker
     */
    @Override
    public void destroy() {
        delegated.stop();
    }

}
