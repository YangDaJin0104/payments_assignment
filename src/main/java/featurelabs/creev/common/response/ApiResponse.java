package featurelabs.creev.common.response;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        int status
) {

    public static <T> ApiResponse<T> success(T data, String message, int status) {
        return new ApiResponse<>(true, "SUCCESS", message, data, status);
    }

    public static ApiResponse<Void> success(String message, int status) {
        return new ApiResponse<>(true, "SUCCESS", message, null, status);
    }

    public static ApiResponse<Void> error(String code, String message, int status) {
        return new ApiResponse<>(false, code, message, null, status);
    }
}
