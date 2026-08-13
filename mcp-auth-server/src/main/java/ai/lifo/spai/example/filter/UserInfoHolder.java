package ai.lifo.spai.example.filter;

/**
 * @author zhanglifeng
 * @since 2026-04-29
 */
public class UserInfoHolder {

    //pass userInfo to tool methods, use InheritableThreadLocal can pass userInfo to child threads
    public static final InheritableThreadLocal<String> userInfoContext = new InheritableThreadLocal<>();

    public static String getUserInfo() {
        return userInfoContext.get();
    }

    public static void setUserInfo(String userInfo) {
        userInfoContext.set(userInfo);
    }

}
