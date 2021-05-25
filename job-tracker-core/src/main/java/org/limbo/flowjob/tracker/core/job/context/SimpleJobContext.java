package org.limbo.flowjob.tracker.core.job.context;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.limbo.flowjob.tracker.core.job.attribute.ImmutableJobAttribute;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Brozen
 * @since 2021-05-21
 */
@Slf4j
public class SimpleJobContext extends AbstractJobContext implements JobContext {

    /**
     * 作业ID
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String jobId;

    /**
     * 执行上下文ID
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String contextId;

    /**
     * 此上下文状态
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private Status status;

    /**
     * 此分发执行此作业上下文的worker
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private String workerId;

    /**
     * 此上下文的创建时间
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private LocalDateTime createdAt;

    /**
     * 此上下文的更新时间
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private LocalDateTime updatedAt;

    /**
     * 作业属性，不可变。
     */
    @Getter
    private ImmutableJobAttribute jobAttributes;

    public SimpleJobContext(String jobId, String contextId, Status status, String workerId,
                            JobContextRepository jobContextRepository) {
        this(jobId, contextId, status, workerId, Collections.emptyMap(), jobContextRepository);
    }

    public SimpleJobContext(String jobId, String contextId, Status status, String workerId,
                            Map<String, List<String>> attributes,
                            JobContextRepository jobContextRepository) {
        super(jobContextRepository);
        this.jobId = jobId;
        this.contextId = contextId;
        this.status = status;
        this.workerId = workerId;

        this.jobAttributes = new ImmutableJobAttribute(attributes);
    }

}