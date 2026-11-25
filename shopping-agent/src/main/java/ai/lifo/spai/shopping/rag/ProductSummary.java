package ai.lifo.spai.shopping.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSummary(
        String id,
        String title,
        String category,
        String subCategory,
        String brand,
        String model,
        BigDecimal price,
        BigDecimal mrp,
        BigDecimal averageRating,
        Integer reviewCount,
        Integer stockQuantity,
        String imageUrl,
        String description
) {

}