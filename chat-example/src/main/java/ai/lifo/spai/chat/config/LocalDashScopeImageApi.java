/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.lifo.spai.chat.config;

import com.alibaba.cloud.ai.dashscope.api.ApiUtils;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec.DashScopeImageGenerationRequest;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec.DashScopeImageGenerationRequest.DashScopeImageGenerationRequestInput;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec.DashScopeImageGenerationRequest.DashScopeImageGenerationRequestInputMessage;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec.DashScopeImageGenerationRequest.DashScopeImageGenerationRequestInputMessageContent;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec.DashScopeImageRequest;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.*;
import static com.alibaba.cloud.ai.dashscope.spec.DashScopeModel.ImageModel.*;

/**
 * 自定义的DashScopeImageApi 修复bug
 *
 * @author zhanglifeng
 * @since 2026-09-22
 */
public class LocalDashScopeImageApi extends DashScopeImageApi {

    private final String imagesPath;

    private final RestClient restClient;

    public LocalDashScopeImageApi(String baseUrl,
                                  ApiKey apiKey,
                                  String imagesPath,
                                  String queryTaskPath,
                                  String workSpaceId,
                                  RestClient.Builder restClientBuilder,
                                  ResponseErrorHandler responseErrorHandler) {
        super(baseUrl, apiKey, imagesPath, queryTaskPath, workSpaceId, restClientBuilder, responseErrorHandler);
        this.imagesPath = imagesPath;
        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(ApiUtils.getJsonContentHeaders(apiKey.getValue(), workSpaceId))
                .defaultStatusHandler(responseErrorHandler)
                .build();
    }

    @Override
    public ResponseEntity<DashScopeApiSpec.DashScopeImageAsyncResponse> submitImageGenTask(DashScopeImageRequest request, boolean needsAsync) {

        String model = request.model();
        ImageApiPath path = resolveImagePath(model);
        String imagesUri = path.uri;
        Object requestBody = switch (path.bodyType) {
            case IMAGE_GENERATION -> convertToImageGenerationRequest(request);
            case OUT_PAINTING -> convertToOutPaintingRequest(request);
            default -> request;
        };

        var requestBuilder = restClient.post()
                .uri(imagesUri)
                .body(requestBody);

        if (needsAsync) {
            requestBuilder.header(HEADER_ASYNC, ENABLED);
        }

        return requestBuilder
                .retrieve()
                .toEntity(DashScopeApiSpec.DashScopeImageAsyncResponse.class);
    }

    private DashScopeImageGenerationRequest convertToImageGenerationRequest(DashScopeImageRequest request) {
        List<DashScopeImageGenerationRequestInputMessageContent> content = getDashScopeImageGenerationRequestInputMessageContents(request);
        List<DashScopeImageGenerationRequestInputMessage> imageGenerationRequestInputMessages = new ArrayList<>();
        imageGenerationRequestInputMessages.add(new DashScopeImageGenerationRequestInputMessage("user",content));
        return new DashScopeApiSpec.DashScopeImageGenerationRequest(
                request.model(),
                new DashScopeImageGenerationRequestInput(imageGenerationRequestInputMessages),
                new DashScopeApiSpec.DashScopeImageGenerationRequest.DashScopeImageGenerationRequestParameter(
                        request.parameters().negativePrompt(),
                        request.parameters().size(),
                        request.parameters().enableInterleave(),
                        request.parameters().n(),
                        request.parameters().maxImages(),
                        request.parameters().seed(),
                        request.parameters().promptExtend(),
                        request.parameters().watermark()
                ));
    }

    private DashScopeApiSpec.DashScopeOutPaintingRequest convertToOutPaintingRequest(DashScopeImageRequest request) {
        return new DashScopeApiSpec.DashScopeOutPaintingRequest(
                request.model(),
                new DashScopeApiSpec.DashScopeOutPaintingRequest.DashScopeOutPaintingRequestInput(
                        request.input().baseImageUrl()),
                new DashScopeApiSpec.DashScopeOutPaintingRequest.DashScopeOutPaintingRequestParameter(
                        request.parameters().outputRatio(),
                        request.parameters().xScale(),
                        request.parameters().yScale(),
                        request.parameters().angle(),
                        request.parameters().leftOffset(),
                        request.parameters().rightOffset(),
                        request.parameters().topOffset(),
                        request.parameters().bottomOffset(),
                        request.parameters().bestQuality(),
                        request.parameters().limitImageSize()));
    }

    @NonNull
    private List<DashScopeImageGenerationRequestInputMessageContent> getDashScopeImageGenerationRequestInputMessageContents(
            DashScopeImageRequest request) {
        String prompt = request.input().prompt();
        String baseImageUrl = request.input().baseImageUrl();
        List<DashScopeImageGenerationRequestInputMessageContent> content = new ArrayList<>();

        if (prompt != null && !prompt.isEmpty()){
            DashScopeImageGenerationRequestInputMessageContent promptContent = new DashScopeImageGenerationRequestInputMessageContent(prompt,null);
            content.add(promptContent);
        }
        if (baseImageUrl != null && !baseImageUrl.isEmpty()){
            DashScopeImageGenerationRequestInputMessageContent imageContent = new DashScopeImageGenerationRequestInputMessageContent(
                    null, baseImageUrl);
            content.add(imageContent);
        }
        return content;
    }

    /**
     * Resolves API path and request body type from model name.
     * Mapping per Aliyun Bailian: text-to-image, image-to-image, multimodal, image-generation docs.
     * 就是这个方法，新模型路径可能有问题。还需要注意同步异步路径不同！
     */
    private ImageApiPath resolveImagePath(String model) {
        // wan2.6-image: Wanx 2.6 async -> image-generation/generation + dedicated request body
        if (WAN_2_6_IMAGE.getValue().equals(model) || model.startsWith("qwen-image")) {
            return new ImageApiPath(IMAGE_GENERATION_RESTFUL_URL, RequestBodyType.IMAGE_GENERATION);
        }
        // image-out-painting: AI out-painting -> image2image/out-painting
        if (IMAGE_OUT_PAINTING.getValue().equals(model)) {
            return new ImageApiPath(OUT_PAINTING_RESTFUL_URL, RequestBodyType.OUT_PAINTING);
        }
        // qwen-mt-image: Qwen image translation -> image2image/image-synthesis
        if (QWEN_MT_IMAGE.getValue().equals(model)) {
            return new ImageApiPath(IMAGE2IMAGE_RESTFUL_URL, RequestBodyType.STANDARD);
        }

        // qwen-image*, z-image* (incl. qwen-image-edit*): Qwen text-to-image/edit, Z-Image -> multimodal-generation/generation
//        if (model.startsWith("qwen-image") || model.startsWith("z-image")) {
//            return new ImageApiPath(MULTIMODAL_GENERATION_RESTFUL_URL, RequestBodyType.STANDARD);
//        }
        // Wanx 2.5 and below, wanx series: text2image/image-synthesis
        return new ImageApiPath(imagesPath, RequestBodyType.STANDARD);
    }

    enum RequestBodyType { STANDARD, IMAGE_GENERATION, OUT_PAINTING }

    private record ImageApiPath(String uri, RequestBodyType bodyType) { }


    public static Builder localBuilder() {
        return new Builder();
    }


    public static class Builder {

        private String baseUrl = DEFAULT_BASE_URL;

        private ApiKey apiKey;

        private String imagesPath = TEXT2IMAGE_RESTFUL_URL;

        private String queryTaskPath = QUERY_TASK_RESTFUL_URL;

        private String workSpaceId;

        private RestClient.Builder restClientBuilder = RestClient.builder();

        private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

        public Builder() {
        }

        // Copy constructor for mutate()
//        public Builder(DashScopeImageApi api) {
//            this.baseUrl = api.getBaseUrl();
//            this.apiKey = api.getApiKey();
//            this.imagesPath = api.imagesPath;
//            this.queryTaskPath = api.queryTaskPath;
//            this.restClientBuilder = api.restClient != null ? api.restClient.mutate() : RestClient.builder();
//            this.responseErrorHandler = api.getResponseErrorHandler();
//        }

        public LocalDashScopeImageApi.Builder baseUrl(String baseUrl) {

            Assert.notNull(baseUrl, "Base URL cannot be null");
            this.baseUrl = baseUrl;
            return this;
        }

        public LocalDashScopeImageApi.Builder workSpaceId(String workSpaceId) {
            // Workspace ID is optional, but if provided, it must not be null.
            if (StringUtils.hasText(workSpaceId)) {
                Assert.notNull(workSpaceId, "Workspace ID cannot be null");
            }
            this.workSpaceId = workSpaceId;
            return this;
        }

        public LocalDashScopeImageApi.Builder apiKey(String simpleApiKey) {
            Assert.notNull(simpleApiKey, "Simple api key cannot be null");
            this.apiKey = new SimpleApiKey(simpleApiKey);
            return this;
        }

        public LocalDashScopeImageApi.Builder imagesPath(String imagesPath) {
            Assert.notNull(imagesPath, "Images path cannot be null");
            this.imagesPath = imagesPath;
            return this;
        }

        public LocalDashScopeImageApi.Builder queryTaskPath(String queryTaskPath) {
            Assert.notNull(queryTaskPath, "Query task path cannot be null");
            this.queryTaskPath = queryTaskPath;
            return this;
        }

        public LocalDashScopeImageApi.Builder restClientBuilder(RestClient.Builder restClientBuilder) {
            Assert.notNull(restClientBuilder, "Rest client builder cannot be null");
            this.restClientBuilder = restClientBuilder;
            return this;
        }

        public LocalDashScopeImageApi.Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
            Assert.notNull(responseErrorHandler, "Response error handler cannot be null");
            this.responseErrorHandler = responseErrorHandler;
            return this;
        }

        public LocalDashScopeImageApi build() {

            Assert.notNull(apiKey, "API key cannot be null");

            return new LocalDashScopeImageApi(this.baseUrl,
                    this.apiKey,
                    this.imagesPath,
                    this.queryTaskPath,
                    this.workSpaceId,
                    this.restClientBuilder,
                    this.responseErrorHandler);
        }

    }
}
