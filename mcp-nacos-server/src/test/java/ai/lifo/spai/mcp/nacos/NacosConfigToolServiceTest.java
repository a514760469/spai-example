package ai.lifo.spai.mcp.nacos;

import ai.lifo.spai.mcp.nacos.model.GetConfigRequest;
import ai.lifo.spai.mcp.nacos.model.ListConfigsRequest;
import ai.lifo.spai.mcp.nacos.model.SearchConfigRequest;
import ai.lifo.spai.mcp.nacos.service.NacosClientFactory;
import ai.lifo.spai.mcp.nacos.service.NacosConfigToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NacosConfigToolService 手动测试类。
 * <p>
 * 使用前请修改下方的连接参数常量，然后逐个运行测试方法。
 * </p>
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
class NacosConfigToolServiceTest {

    // ======================== 修改为你的 Nacos 连接信息 ========================

    private static final String SERVER_ADDR = "mse-6a159390-nacos-ans.mse.aliyuncs.com:8848";
    private static final String NAMESPACE = "jdd_config";

    // 用户名/密码 鉴权（不用则留空）
    private static final String USERNAME = "nacos";
    private static final String PASSWORD = "nacos";

    // AccessKey/SecretKey 鉴权（不用则留空）
    private static final String ACCESS_KEY = "";
    private static final String SECRET_KEY = "";

    // 测试用的配置项
    private static final String TEST_DATA_ID = "ykd-wechat-gateway-test.yaml";
    private static final String TEST_GROUP = "JDD_GROUP";
    private static final String SEARCH_KEYWORD = "jdd*";

    // ======================== 初始化 ========================

    private NacosConfigToolService toolService;

    @BeforeEach
    void setUp() {
        toolService = new NacosConfigToolService(new NacosClientFactory());
    }

    // ======================== 测试方法 ========================

    /**
     * 测试获取指定配置内容。
     */
    @Test
    void testGetConfig() {
        var req = new GetConfigRequest(SERVER_ADDR, "jdd_config", "", "",
                ACCESS_KEY, SECRET_KEY, "metric-monitor-client.yaml", "JDD_GROUP");

        String result = toolService.getConfig(req);
        System.out.println("===== getConfig 结果 =====");
        System.out.println(result);
    }

    /**
     * 测试列出配置列表。
     */
    @Test
    void testListConfigs() {
        var req = new ListConfigsRequest(SERVER_ADDR, NAMESPACE, "", "",
                ACCESS_KEY, SECRET_KEY, TEST_GROUP, 1, 20);
        String result = toolService.listConfigs(req);
        System.out.println("===== listConfigs 结果 =====");
        System.out.println(result);
    }

    /**
     * 测试模糊搜索配置。
     */
    @Test
    void testSearchConfig() {
        var req = new SearchConfigRequest(SERVER_ADDR, NAMESPACE, "", "",
                ACCESS_KEY, SECRET_KEY, SEARCH_KEYWORD, TEST_GROUP, 1, 20);
        String result = toolService.searchConfig(req);
        System.out.println("===== searchConfig 结果 =====");
        System.out.println(result);
    }
}
