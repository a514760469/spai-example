package ai.lifo.spai.chat.config;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.ResolvedConnectionProperties;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import static com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionUtils.resolveConnectionProperties;

/**
 *
 * 改用自定义的LocalDashScopeImageApi 修复包中存在的bug
 *
 * @author zhanglifeng
 * @since 2026-09-22
 */
@Configuration
public class ImageModelConfig {


    @Bean
    @ConditionalOnMissingBean
    public DashScopeImageModel dashScopeImageModel(DashScopeConnectionProperties commonProperties,
                                                   DashScopeImageProperties imageProperties,
                                                   ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                                   ObjectProvider<RetryTemplate> retryTemplate,
                                                   ObjectProvider<ResponseErrorHandler> responseErrorHandler,
                                                   ObjectProvider<ObservationRegistry> observationRegistry,
                                                   ObjectProvider<ImageModelObservationConvention> observationConvention) {
        ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties, imageProperties, "image");

        var dashScopeImageApi = LocalDashScopeImageApi.localBuilder()
                .apiKey(resolved.apiKey())
                .baseUrl(resolved.baseUrl())
                .imagesPath(imageProperties.getImagesPath())
                .queryTaskPath(imageProperties.getQueryTaskPath())
                .workSpaceId(resolved.workspaceId())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .responseErrorHandler(responseErrorHandler.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();

        var dashScopeImageModel = DashScopeImageModel.builder()
                .dashScopeApi(dashScopeImageApi)
                .defaultOptions(imageProperties.getOptions())
                .retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();

        observationConvention.ifAvailable(dashScopeImageModel::setObservationConvention);

        return dashScopeImageModel;
    }


}
