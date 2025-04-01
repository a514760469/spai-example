package ai.credithc.xd.late;

/**
 * @author zhanglifeng
 * @since 2025-03-28
 */
class SpringAIMcpApplicationTest {
    public static void main(String[] args) {
//        String filePath = SpringAIMcpApplication.getFilePath();
        String getenv = System.getenv("spring-ai-mcp-overview.txt");
        System.out.println(STR."filePath = \{SpringAIMcpApplication.getDbPath()}");
    }
}