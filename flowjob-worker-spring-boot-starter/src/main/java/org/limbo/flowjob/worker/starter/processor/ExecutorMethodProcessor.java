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

package org.limbo.flowjob.worker.starter.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.limbo.flowjob.worker.core.domain.Task;
import org.limbo.flowjob.worker.core.executor.TaskExecutor;
import org.limbo.flowjob.worker.starter.processor.event.ExecutorScannedEvent;
import org.limbo.flowjob.worker.starter.processor.event.WorkerReadyEvent;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参考：EventListenerMethodProcessor
 * 用户处理 Spring 中注册的 TaskExecutor 类型 Bean，或者使用 @Executor 注解标记的方法，放入全局的 Worker 单例中去。
 *
 * @author Brozen
 * @since 2022-09-07
 */
@Slf4j
public class ExecutorMethodProcessor implements SmartInitializingSingleton,
        ApplicationContextAware, BeanFactoryAware, ApplicationEventPublisherAware {

    private static final Method M_RUN;
    static {
        try {
            M_RUN = TaskExecutor.class.getMethod("run", Task.class);
            if (M_RUN.getReturnType() != Void.TYPE) {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Can't find \"void run(Task task)\" method in " +
                    "org.limbo.flowjob.worker.core.executor.ExecuteContext");
        }
    }

    private ConfigurableApplicationContext applicationContext;

    private ConfigurableListableBeanFactory beanFactory;

    private ApplicationEventPublisher eventPublisher;

    private final boolean autoRegister;

    public ExecutorMethodProcessor(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }

    /**
     * 记录忽略处理的 Class，用于加速初始化的 Bean 扫描阶段
     */
    private final Set<Class<?>> ignoredClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    /**
     * 注入的 ApplicationContext 需要是 ConfigurableApplicationContext 类型
     */
    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) {
        Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
                "ApplicationContext does not implement ConfigurableApplicationContext");
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }


    /**
     * 注入的 BeanFactory 需要是 ConfigurableListableBeanFactory 类型
     */
    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        Assert.isTrue(beanFactory instanceof ConfigurableListableBeanFactory,
                "BeanFactory does not implement ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }


    /**
     * 注入事件发布器，用于通知 Worker 的 Executor 扫描完成，可以进行 Worker 初始化。
     * 会通过发出 {@link WorkerReadyEvent} 事件来通知。
     */
    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * 扫描所有声明的 Bean，解析为 TaskExecutor
     */
    @Override
    public void afterSingletonsInstantiated() {
        Assert.state(this.applicationContext != null, "No ApplicationContext set");
        String[] beanNames = this.applicationContext.getBeanNamesForType(Object.class);
        List<TaskExecutor> executors = new ArrayList<>();

        for (String beanName : beanNames) {
            // 忽略指定作用域下的代理 Bean
            if (ScopedProxyUtils.isScopedTarget(beanName)) {
                continue;
            }

            // 解析真实 Bean 类型，如果是代理类型需要解开代理封装
            Class<?> beanType;
            try {
                if ((beanType = AutoProxyUtils.determineTargetClass(this.beanFactory, beanName)) == null) {
                    continue;
                }
            } catch (Exception ignore) {
                continue;
            }

            // 是否指定作用域下的 Bean，防止作用域下的代理 Bean 名称未按照 scoped 格式声明
            if (ScopedObject.class.isAssignableFrom(beanType)) {
                Class<?> targetClass = AutoProxyUtils.determineTargetClass(
                        beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
                beanType = targetClass == null ? beanType : targetClass;
            }

            // 解析 Executor
            try {
                executors.addAll(parseExecutor(beanName, beanType));
            } catch (Exception e) {
                throw new BeanInitializationException("Failed to process @Executor annotation " +
                        "or TaskExecutor implementation bean with name '" + beanName + "'", e);
            }
        }

        // 所有 Executor 扫描完成
        eventPublisher.publishEvent(new ExecutorScannedEvent(executors));

        // 根据配置决定是否自动注册
        if (autoRegister) {
            eventPublisher.publishEvent(new WorkerReadyEvent());
        }

    }


    /**
     * 执行将 Bean 解析为 TaskExecutor 的过程
     * @return 解析出的 TaskExecutor
     */
    private List<TaskExecutor> parseExecutor(String beanName, Class<?> beanType) {
        List<TaskExecutor> executors = new ArrayList<>();
        if (this.ignoredClasses.contains(beanType) || isSpringContainerClass(beanType)) {
            return executors;
        }

        Object bean = this.applicationContext.getBean(beanName);

        // 判断 Bean 是否是一个 TaskExecutor
        boolean beanIsTaskExecutor = bean instanceof TaskExecutor;
        if (beanIsTaskExecutor) {
            executors.add((TaskExecutor) bean);
        }

        // 解析 @Executor 注解的方法
        Map<Method, Executor> annotatedMethods = Collections.emptyMap();
        try {
            MethodIntrospector.MetadataLookup<Executor> lookup = method ->
                    AnnotatedElementUtils.findMergedAnnotation(method, Executor.class);
            annotatedMethods = MethodIntrospector.selectMethods(beanType, lookup);
        } catch (Exception e) {
            log.error("Executor annotation method select fail bean:{}", beanName, e);
        }

        // 根据 Bean 类型、@Executor 方法判断是否忽略 Bean
        if (!beanIsTaskExecutor && MapUtils.isEmpty(annotatedMethods)) {
            this.ignoredClasses.add(beanType);
            return executors;
        }

        annotatedMethods.forEach((method, annotation) -> {
            // 如果在子类的 run 方法上再次加了 @Executor 注解，则忽略
            if (beanIsTaskExecutor && isTaskExecutorRunMethod(method)) {
                return;
            }

            executors.add(parseBeanMethodExecutor(bean, method, annotation));
        });

        return executors;
    }


    /**
     * 将 Spring Bean 中 @Executor 注解编辑的方法解析为 BeanMethodExecutor
     */
    private BeanMethodExecutor parseBeanMethodExecutor(Object bean, Method method, Executor annotation) {
        BeanMethodExecutor executor = new BeanMethodExecutor(bean, method);
        if (StringUtils.isNotBlank(annotation.name())) {
            executor.setName(annotation.name());
        }
        if (StringUtils.isNotBlank(annotation.description())) {
            executor.setDescription(annotation.description());
        }

        return executor;
    }


    /**
     * 入参方法是否 TaskExecutor 中的 run 方法，或者 TaskExecutor 实现类中的 run 方法。
     */
    private boolean isTaskExecutorRunMethod(Method method) {
        return Objects.equals(method.getName(), M_RUN.getName())
                && Objects.equals(method.getReturnType(), M_RUN.getReturnType())
                && Arrays.equals(method.getParameterTypes(), M_RUN.getParameterTypes())
                && Modifier.isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers());
    }


    /**
     * 判断 Bean 类型是否来自 Spring 框架，框架内的 Bean 必然没有 @Executor 注解
     */
    private boolean isSpringContainerClass(Class<?> clazz) {
        return clazz.getName().startsWith("org.springframework.");
    }

}
