package ai.lifo.spai.chat.web.dashscope;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.image.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author zhanglifeng
 * @since 2026-09-22
 */
@RequestMapping("/dashscope")
@RestController
@RequiredArgsConstructor
public class DashScopeImageController {

    private final String DEFAULT_IMAGE_MODEL = "qwen-image-3.0-pro";

    private final ImageModel dashScopeImageModel;


    /**
     * 百炼的图片生成，默认是异步的，如果使用不支持异步的模型会有问题，为此我升级了依赖版本，当前版本支持设置同步模式
     */
    @GetMapping("/imageUrl")
    public String image(@RequestParam(defaultValue = "A beautiful space painting") String msg) {

        DashScopeImageOptions dashScopeImageOptions = DashScopeImageOptions.builder()
                .model(DEFAULT_IMAGE_MODEL)
                .build();

        ImageResponse response = dashScopeImageModel.call(new ImagePrompt(msg, dashScopeImageOptions));

        return response.getResult().getOutput().getUrl();
    }

    /**
     * Generates multiple images from single prompt.
     */
    @GetMapping("/image/multiPrompt")
    public ResponseEntity<Collection<String>> generateImageWithMultiPrompt(
            @RequestParam(value = "prompt", defaultValue = "一只会编程的猫") String prompt,
            @RequestParam(defaultValue = "2") int count) {

        ImageOptions options = ImageOptionsBuilder.builder()
                .model(DEFAULT_IMAGE_MODEL)
                .N(count)
                .build();
        ImageResponse response = dashScopeImageModel.call(new ImagePrompt(prompt, options));
        Set<String> imageSet = response.getResults().stream().map(result -> result.getOutput().getUrl()).collect(Collectors.toSet());
        return ResponseEntity.ok(imageSet);
    }


}
