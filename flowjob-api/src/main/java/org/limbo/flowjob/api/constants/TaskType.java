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

package org.limbo.flowjob.api.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 *
 * @author Brozen
 * @since 2021-05-19
 */
public enum TaskType {

    UNKNOWN(ConstantsPool.UNKNOWN, "未知"),
    /**
     * 给一个节点下发的任务
     */
    STANDALONE(1, "单机任务"),
    /**
     * 给每个可选中节点下发任务
     */
    BROADCAST(2, "广播任务"),
    /**
     * 拆分子任务的任务
     * 分割任务 产生后续task的切割情况
     * 后续有且只有一个map任务
     */
    SHARDING(3, "分片任务"),
    /**
     * map任务
     * 根据分片返回值创建对应task
     * 前继有且只有一个分片任务
     */
    MAP(4, "Map任务"),
    /**
     * reduce任务
     * 根据map任务的返回值进行结果处理
     * 前继有且只有一个map任务
     */
    REDUCE(5, "Reduce任务"),
    ;

    @JsonValue
    @Getter
    public final int type;

    @Getter
    public final String desc;

    @JsonCreator
    TaskType(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验值
     */
    public boolean is(TaskType type) {
        return equals(type);
    }

    /**
     * 校验是否是当前状态
     *
     * @param type 待校验状态值
     */
    public boolean is(Number type) {
        return type != null && type.intValue() == this.type;
    }

    /**
     * 解析上下文状态值
     */
    public static TaskType parse(Number type) {
        for (TaskType jobType : values()) {
            if (jobType.is(type)) {
                return jobType;
            }
        }
        return UNKNOWN;
    }

}
