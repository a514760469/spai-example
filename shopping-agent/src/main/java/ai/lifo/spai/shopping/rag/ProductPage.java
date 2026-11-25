package ai.lifo.spai.shopping.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ProductSummary> products
) {

}