package ai.lifo.agent.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 排除一些框架自带的bean
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Component
public class ExcludeBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

    // 使用自己的executionController，需要排除默认的 executionController
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.removeBeanDefinition("executionController");
        registry.removeBeanDefinition("graphController");
        registry.removeBeanDefinition("graphExecutionController");
    }
}
