package ai.lifo.spai.shopping.memory;

/**
 * 子 Agent 的工具/hook 拿到的 threadId 是 {parent}_subgraph_{agent}，
 * 凭证与记忆都挂在父 threadId 下，取身份前必须先还原。
 */
final class ThreadIds {

    private ThreadIds() {
    }

    static String parent(String threadId) {
        if (threadId == null) {
            return null;
        }
        int idx = threadId.indexOf("_subgraph_");
        return idx > 0 ? threadId.substring(0, idx) : threadId;
    }
}