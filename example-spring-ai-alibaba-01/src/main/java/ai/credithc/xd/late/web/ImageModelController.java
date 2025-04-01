package ai.credithc.xd.late.web;

import org.springframework.ai.image.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
@RestController
public class ImageModelController {

    private final ImageModel imageModel;

    public ImageModelController(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @RequestMapping("/image")
    public String image(String input) {
        ImageOptions options = ImageOptionsBuilder.builder()
                .model("wanx-v1")
                .height(1024)
                .width(1024)
                .build();

        ImagePrompt imagePrompt = new ImagePrompt(input, options);
        ImageResponse response = imageModel.call(imagePrompt);
        String imageUrl = response.getResult().getOutput().getUrl();

        return STR."redirect:\{imageUrl}";
    }

}
