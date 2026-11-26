package ai.lifo.spai.shopping.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 移除框架默认的 graphExecutionController，改用本项目的实现。
 *
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Component
public class ExcludeBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
//        registry.removeBeanDefinition("executionController");
//        registry.removeBeanDefinition("graphController");
        registry.removeBeanDefinition("graphExecutionController");
    }
}
